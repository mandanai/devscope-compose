package dev.journeyscope.core

import dev.journeyscope.core.model.DiagnosticMetadata
import dev.journeyscope.core.model.MetadataValue
import dev.journeyscope.core.model.SpanCategory
import dev.journeyscope.core.model.SpanStatus
import dev.journeyscope.core.model.TraceStatus
import dev.journeyscope.core.model.diagnosticMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class JourneyTracerTest {
    @Test
    fun successfulJourney() =
        runTest {
            val clock = FakeJourneyClock()
            val tracer = testTracer(clock = clock)
            val journey = tracer.startJourney("Add to Cart")
            journey.recordEvent("Button Click", SpanCategory.UI_INTERACTION)
            val result =
                journey.traceSuspend("AddToCartUseCase", SpanCategory.USE_CASE) {
                    clock.advance(30.milliseconds)
                    "done"
                }
            journey.recordEvent("CartUiState.Success", SpanCategory.STATE_EMISSION)
            journey.finish()

            val trace = journey.trace()
            assertEquals("done", result)
            assertEquals(TraceStatus.COMPLETED, trace.status)
            assertEquals(SpanStatus.OK, trace.span("AddToCartUseCase").status)
            assertEquals(listOf("Button Click", "CartUiState.Success"), trace.events.map { it.name })
            assertEquals(30.milliseconds, trace.summary?.duration)
            assertEquals(trace.id, trace.correlationId)
        }

    @Test
    fun failedJourneyRethrowsOriginalAndRedactsMessage() =
        runTest {
            val tracer = testTracer()
            val boom = IllegalStateException("card 4111-1111-1111-1111")
            val thrown =
                assertFailsWith<IllegalStateException> {
                    tracer.withJourney("Checkout") { traceSuspend("Pay") { throw boom } }
                }
            assertEquals(boom.message, thrown.message)

            val trace = tracer.recorder.snapshot().single()
            assertEquals(TraceStatus.FAILED, trace.status)
            assertEquals(SpanStatus.FAILED, trace.span("Pay").status)
            assertTrue(
                trace.failure
                    ?.type
                    .orEmpty()
                    .contains("IllegalStateException"),
            )
            assertNull(trace.failure?.message)
            assertNull(trace.span("Pay").failure?.message)
        }

    @Test
    fun failureMessageKeptOnlyWhenPolicyAllows() =
        runTest {
            val config = JourneyScopeConfig(enabled = true, redactionPolicy = RedactionPolicy(includeFailureMessages = true))
            val tracer = testTracer(config)
            runCatching { tracer.withJourney("j") { error("visible") } }
            assertEquals(
                "visible",
                tracer.recorder
                    .snapshot()
                    .single()
                    .failure
                    ?.message,
            )
        }

    @Test
    fun recordFailuresOffDropsFailureInfoButKeepsStatus() =
        runTest {
            val tracer = testTracer(JourneyScopeConfig(enabled = true, recordFailures = false))
            runCatching { tracer.withJourney("j") { traceSuspend("s") { error("x") } } }
            val trace = tracer.recorder.snapshot().single()
            assertEquals(SpanStatus.FAILED, trace.span("s").status)
            assertNull(trace.span("s").failure)
            assertNull(trace.failure)
        }

    @Test
    fun cancellationIsPropagatedAndRecorded() =
        runTest {
            val tracer = testTracer()
            var caught: Throwable? = null
            val job =
                launch {
                    try {
                        tracer.withJourney("Slow") { traceSuspend("Wait") { delay(10_000) } }
                    } catch (e: CancellationException) {
                        caught = e
                        throw e
                    }
                }
            runCurrent()
            job.cancelAndJoin()

            val trace = tracer.recorder.snapshot().single()
            assertTrue(caught is CancellationException)
            assertEquals(TraceStatus.CANCELLED, trace.status)
            assertEquals(SpanStatus.CANCELLED, trace.span("Wait").status)
        }

    @Test
    fun nestedSpansFormHierarchy() =
        runTest {
            val tracer = testTracer()
            val journey = tracer.startJourney("Nested")
            journey.traceSuspend("outer") { journey.traceSuspend("middle") { journey.traceSuspend("inner") {} } }
            val trace = journey.trace()
            assertNull(trace.span("outer").parentId)
            assertEquals(trace.span("outer").id, trace.span("middle").parentId)
            assertEquals(trace.span("middle").id, trace.span("inner").parentId)
        }

    @Test
    fun concurrentChildrenKeepTheirOwnParents() =
        runTest {
            val tracer = testTracer()
            val journey = tracer.startJourney("Fan out")
            journey.traceSuspend("root") {
                coroutineScope {
                    (1..5)
                        .map { i ->
                            async {
                                journey.traceSuspend("child$i") { journey.traceSuspend("grandchild$i") { delay(1) } }
                            }
                        }.awaitAll()
                }
            }
            val trace = journey.trace()
            val root = trace.span("root")
            (1..5).forEach { i ->
                assertEquals(root.id, trace.span("child$i").parentId)
                assertEquals(trace.span("child$i").id, trace.span("grandchild$i").parentId)
            }
        }

    @Test
    fun contextSurvivesDispatcherSwitch() =
        runTest {
            val tracer = testTracer()
            val journey = tracer.startJourney("Switch")
            journey.traceSuspend("outer") {
                withContext(Dispatchers.Default) { journey.traceSuspend("background") { delay(1) } }
            }
            val trace = journey.trace()
            assertEquals(trace.span("outer").id, trace.span("background").parentId)
        }

    @Test
    fun manuallyLaunchedWorkCanBeBoundExplicitly() =
        runTest {
            val tracer = testTracer()
            val journey = tracer.startJourney("Manual")
            val root = journey.beginSpan("root")
            launch(Dispatchers.Default + journey.context(root)) { journey.traceSuspend("bound") { delay(1) } }.join()
            launchInJourney(journey, root, Dispatchers.Default) { journey.traceSuspend("helper") { delay(1) } }.join()
            root.end()
            assertEquals(root.id, journey.trace().span("bound").parentId)
            assertEquals(root.id, journey.trace().span("helper").parentId)
        }

    @Test
    fun concurrentJourneysAreIsolated() =
        runTest {
            val tracer = testTracer()
            val a = tracer.startJourney("A")
            val b = tracer.startJourney("B")
            a.traceSuspend("a-outer") { b.traceSuspend("b-inside-a") {} }

            assertEquals(1, a.trace().spans.size)
            assertNull(b.trace().span("b-inside-a").parentId)
            assertNotEquals(a.id, b.id)
        }

    @Test
    fun syncTraceNestsExplicitlyAndRethrows() {
        val journey = testTracer().startJourney("Sync")
        val boom = IllegalArgumentException()
        val thrown =
            assertFailsWith<IllegalArgumentException> {
                journey.trace("outer") { outer -> journey.trace("inner", parent = outer) { throw boom } }
            }
        assertSame(boom, thrown)
        val trace = journey.trace()
        assertEquals(trace.span("outer").id, trace.span("inner").parentId)
        assertEquals(SpanStatus.FAILED, trace.span("outer").status)
    }

    @Test
    fun overlappingChildrenAreNeverDoubleCounted() {
        val clock = FakeJourneyClock()
        val journey = testTracer(clock = clock).startJourney("Overlap")
        clock.advance(5.milliseconds)
        val parent = journey.beginSpan("parent")
        clock.advance(10.milliseconds)
        val first = journey.beginSpan("first", parent = parent)
        clock.advance(20.milliseconds)
        val second = journey.beginSpan("second", parent = parent)
        clock.advance(10.milliseconds)
        first.end()
        clock.advance(20.milliseconds)
        second.end()
        clock.advance(40.milliseconds)
        parent.end()
        journey.finish()

        val trace = journey.trace()
        val timing = trace.timing()
        val parentTiming = timing.spans.single { it.spanId == parent.id }
        assertEquals(105.milliseconds, timing.duration)
        assertEquals(100.milliseconds, parentTiming.inclusive)
        assertEquals(50.milliseconds, parentTiming.exclusive)
        assertEquals(30.milliseconds, timing.spans.single { it.spanId == first.id }.inclusive)
        assertEquals(1, timing.gaps.size)
        assertEquals(5.milliseconds, timing.gaps.single().duration)
        assertEquals(listOf(parent.id, second.id), timing.criticalPath)
        assertEquals(100.milliseconds, timing.criticalPathDuration)
    }

    @Test
    fun eventOffsetsAreNotDurations() {
        val clock = FakeJourneyClock()
        val journey = testTracer(clock = clock).startJourney("Events")
        clock.advance(7.milliseconds)
        journey.recordEvent("tap")
        val trace = journey.trace()
        assertEquals(7.milliseconds, trace.offsetFromStart(trace.events.single()))
        assertTrue(trace.timing().spans.isEmpty())
    }

    @Test
    fun unfinishedSpansAreInterruptedNotSuccessful() {
        val clock = FakeJourneyClock()
        val journey = testTracer(clock = clock).startJourney("Unfinished")
        journey.beginSpan("hanging")
        clock.advance(10.milliseconds)

        val live = journey.trace().timing(nowNanos = clock.monotonicNanos())
        assertTrue(live.spans.single().isOpen)
        assertEquals(10.milliseconds, live.spans.single().inclusive)

        journey.finish()
        val span = journey.trace().span("hanging")
        assertEquals(SpanStatus.INTERRUPTED, span.status)
        assertTrue(span.endedBySystem)
        assertFalse(
            journey
                .trace()
                .timing()
                .spans
                .single()
                .isOpen,
        )
    }

    @Test
    fun interruptActiveMarksRunningJourneysInterrupted() {
        val tracer = testTracer()
        val running = tracer.startJourney("running")
        val done = tracer.startJourney("done").also { it.finish() }
        tracer.interruptActive("app backgrounded")
        assertEquals(TraceStatus.INTERRUPTED, running.status)
        assertEquals("app backgrounded", running.trace().failure?.message)
        assertEquals(TraceStatus.COMPLETED, done.status)
    }

    @Test
    fun lifecycleTransitionsAreConsistent() {
        val journey = testTracer().createJourney("lifecycle")
        assertEquals(TraceStatus.CREATED, journey.status)
        journey.start()
        assertEquals(TraceStatus.RUNNING, journey.status)
        journey.finish()
        journey.fail(IllegalStateException())
        journey.cancel()
        journey.recordEvent("late")
        assertEquals(TraceStatus.COMPLETED, journey.status)
        assertTrue(journey.trace().events.isEmpty())
        assertEquals(1, journey.trace().droppedItems)
    }

    @Test
    fun createdJourneyStartsImplicitlyOnFirstWrite() {
        val journey = testTracer().createJourney("implicit")
        journey.recordEvent("first")
        assertEquals(TraceStatus.RUNNING, journey.status)
    }

    @Test
    fun disabledSdkRecordsNothingAndKeepsResults() =
        runTest {
            val tracer = testTracer(JourneyScopeConfig(enabled = false))
            val journey = tracer.startJourney("off")
            val value = journey.traceSuspend("op") { 42 }
            val sync = journey.trace("sync") { "ok" }
            val viaScope = tracer.withJourney("scoped") { traceSuspend("x") { 7 } }
            journey.recordEvent("ignored")
            journey.finish()

            assertFalse(journey.isRecording)
            assertEquals(42, value)
            assertEquals("ok", sync)
            assertEquals(7, viaScope)
            assertTrue(tracer.recorder.snapshot().isEmpty())
            assertNull(journey.snapshot())
        }

    @Test
    fun disabledSdkStillRethrowsApplicationErrors() =
        runTest {
            val tracer = testTracer(JourneyScopeConfig(enabled = false))
            assertFailsWith<IllegalStateException> { tracer.withJourney("off") { traceSuspend("x") { error("app") } } }
        }

    @Test
    fun zeroSamplingRateRecordsNothing() {
        val tracer = testTracer(JourneyScopeConfig(enabled = true, samplingRate = 0.0))
        assertFalse(tracer.startJourney("sampled-out").isRecording)
        assertTrue(tracer.recorder.snapshot().isEmpty())
    }

    @Test
    fun ringBufferEvictsOldestFinishedFirst() {
        val tracer = testTracer(JourneyScopeConfig(enabled = true, maxTraces = 3))
        val running = tracer.startJourney("running")
        val finished = tracer.startJourney("finished").also { it.finish() }
        tracer.startJourney("c")
        tracer.startJourney("d")

        val names = tracer.recorder.snapshot().map { it.name }
        assertEquals(listOf("running", "c", "d"), names)
        assertTrue(running.isRecording)
        assertNull(tracer.recorder.find(finished.id))
    }

    @Test
    fun evictedRunningJourneyStopsRecording() {
        val tracer = testTracer(JourneyScopeConfig(enabled = true, maxTraces = 1))
        val first = tracer.startJourney("first")
        tracer.startJourney("second")
        first.recordEvent("lost")
        assertEquals(listOf("second"), tracer.recorder.snapshot().map { it.name })
        assertTrue(first.trace().events.isEmpty())
    }

    @Test
    fun eventBudgetDropsNewItemsButStillClosesAdmittedSpans() {
        val tracer = testTracer(JourneyScopeConfig(enabled = true, maxEventsPerTrace = 3))
        val journey = tracer.startJourney("bounded")
        val span = journey.beginSpan("kept")
        journey.recordEvent("one")
        journey.recordEvent("two")
        journey.recordEvent("dropped")
        assertFalse(journey.beginSpan("dropped-span").isRecording)
        span.end()

        val trace = journey.trace()
        assertEquals(3, trace.spans.size + trace.events.size)
        assertEquals(2, trace.droppedItems)
        assertEquals(SpanStatus.OK, trace.span("kept").status)
    }

    @Test
    fun recorderSurvivesConcurrentJourneysAndWrites() =
        runTest {
            val tracer = testTracer(JourneyScopeConfig(enabled = true, maxTraces = 1_000, maxEventsPerTrace = 1_000))
            val shared = tracer.startJourney("shared")
            withContext(Dispatchers.Default) {
                (1..8)
                    .map { worker ->
                        async {
                            repeat(50) { shared.recordEvent("w$worker-$it") }
                            repeat(25) { i ->
                                val own = tracer.startJourney("own-$worker-$i")
                                own.traceSuspend("op") { own.recordEvent("e") }
                                own.finish()
                            }
                        }
                    }.awaitAll()
            }
            val traces = tracer.recorder.snapshot()
            val sharedTrace = traces.single { it.name == "shared" }
            assertEquals(400, sharedTrace.events.size)
            assertEquals(
                400,
                sharedTrace.events
                    .map { it.sequence }
                    .toSet()
                    .size,
            )
            assertEquals(200, traces.count { it.name.startsWith("own-") && it.status == TraceStatus.COMPLETED })
            assertEquals(traces.size, traces.map { it.id }.toSet().size)
        }

    @Test
    fun equalTimestampsKeepRecordingOrder() {
        val journey = testTracer().startJourney("order")
        listOf("a", "b", "c", "d").forEach { journey.recordEvent(it) }
        assertEquals(listOf("a", "b", "c", "d"), journey.trace().events.map { it.name })
    }

    @Test
    fun metadataIsRedactedAndBounded() {
        val tracer = testTracer(JourneyScopeConfig(enabled = true, maxMetadataSize = 24))
        val journey = tracer.startJourney("meta")
        journey.recordEvent(
            "e",
            metadata =
                diagnosticMetadata {
                    put("authToken", "super-secret-value")
                    put("note", "x".repeat(500))
                    put("later", "dropped")
                },
        )
        val metadata =
            journey
                .trace()
                .events
                .single()
                .metadata
        assertEquals(MetadataValue.Text("[REDACTED]"), metadata["authToken"])
        assertTrue(metadata.truncated)
        val size = metadata.entries.entries.sumOf { (k, v) -> k.length + (v as? MetadataValue.Text)?.value?.length.orZero() }
        assertTrue(size <= 24, "metadata size $size exceeds budget")
    }

    @Test
    fun metadataOnlyAcceptsPrimitiveValues() {
        val metadata =
            diagnosticMetadata {
                put("count", 3)
                put("ratio", 0.5)
                put("flag", true)
            }
        assertEquals(MetadataValue.Integer(3), metadata["count"])
        assertEquals(MetadataValue.Decimal(0.5), metadata["ratio"])
        assertEquals(MetadataValue.Flag(true), metadata["flag"])
        assertTrue(DiagnosticMetadata.Empty.isEmpty)
    }

    @Test
    fun slowJourneyIsFlagged() {
        val clock = FakeJourneyClock()
        val tracer = testTracer(JourneyScopeConfig(enabled = true, slowJourneyThreshold = 100.milliseconds), clock)
        val journey = tracer.startJourney("slow")
        clock.advance(150.milliseconds)
        journey.finish()
        assertTrue(journey.trace().summary?.isSlow == true)
    }

    @Test
    fun configRejectsInvalidValues() {
        assertFailsWith<IllegalArgumentException> { JourneyScopeConfig(maxTraces = 0) }
        assertFailsWith<IllegalArgumentException> { JourneyScopeConfig(maxEventsPerTrace = 0) }
        assertFailsWith<IllegalArgumentException> { JourneyScopeConfig(samplingRate = 1.5) }
        assertFailsWith<IllegalArgumentException> { JourneyScopeConfig(maxMetadataSize = -1) }
    }

    private fun Int?.orZero(): Int = this ?: 0
}
