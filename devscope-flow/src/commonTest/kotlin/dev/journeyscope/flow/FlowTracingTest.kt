package dev.journeyscope.flow

import dev.journeyscope.core.Journey
import dev.journeyscope.core.JourneyScopeConfig
import dev.journeyscope.core.JourneyTracer
import dev.journeyscope.core.model.JourneyEvent
import dev.journeyscope.core.model.MetadataValue
import dev.journeyscope.core.model.SpanCategory
import dev.journeyscope.core.model.SpanStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FlowTracingTest {
    private fun newJourney(): Journey = JourneyTracer(JourneyScopeConfig(enabled = true)).startJourney("J")

    private fun Journey.events(): List<JourneyEvent> = checkNotNull(snapshot()).events

    private fun JourneyEvent.text(key: String): String? = (metadata[key] as? MetadataValue.Text)?.value

    private fun List<JourneyEvent>.states(): List<String?> = map { it.text("state.event") }

    @Test
    fun tracedFlowEmitsSameValuesInSameOrder() = runTest {
        val journey = newJourney()
        val plain = flowOf(1, 2, 3).map { it * 2 }.toList()
        val traced = journey.traceSuspend("collect") {
            flowOf(1, 2, 3).traceFlow("numbers").map { it * 2 }.toList()
        }
        assertEquals(plain, traced)
        assertEquals(
            listOf("started", "upstream", "observed", "upstream", "observed", "upstream", "observed", "completed"),
            journey.events().states(),
        )
    }

    @Test
    fun upstreamFailureIsRethrownUnchangedAndRecorded() = runTest {
        val journey = newJourney()
        val boom = IllegalStateException("boom")
        val thrown = assertFailsWith<IllegalStateException> {
            journey.traceSuspend("collect") {
                flow<Int> { emit(1); throw boom }.traceFlow("numbers").toList()
            }
        }
        assertEquals("boom", thrown.message)
        val last = journey.events().last()
        assertEquals("failed", last.text("state.event"))
        assertEquals("IllegalStateException", last.text("state.failure"))
        assertEquals(null, last.text("state.message"))
    }

    @Test
    fun collectorCancellationIsRecorded() = runTest {
        val journey = newJourney()
        val started = CompletableDeferred<Unit>()
        val job = launch(journey.context()) {
            flow<Int> { emit(1); started.complete(Unit); awaitCancellationForever() }.traceFlow("numbers").collect {}
        }
        started.await()
        job.cancelAndJoin()
        assertEquals("cancelled", journey.events().last().text("state.event"))
    }

    @Test
    fun stateFlowConflationIsVisibleAsFewerObservations() = runTest {
        val journey = newJourney()
        val state = MutableStateFlow(0)
        val seen = mutableListOf<Int>()
        val job = launch(journey.context()) {
            state.observeWithJourneyScope("count") { seen += it }
        }
        state.value = 1
        state.value = 2
        state.value = 3
        runCurrent()
        job.cancelAndJoin()
        assertEquals(listOf(3), seen)
        val observed = journey.events().filter { it.text("state.event") == "observed" }
        assertEquals(1, observed.size)
        assertEquals("Int", observed.single().text("state.current"))
    }

    @Test
    fun stateObservationsCarryPreviousAndCurrentSummaries() = runTest {
        val journey = newJourney()
        val state = MutableStateFlow("a")
        val options = FlowTraceOptions<String>(formatter = { it.uppercase() })
        val job = launch(journey.context()) { state.observeWithJourneyScope("s", options) }
        runCurrent()
        state.value = "b"
        runCurrent()
        job.cancelAndJoin()
        val observed = journey.events().filter { it.text("state.event") == "observed" }
        assertEquals(listOf("A", "B"), observed.map { it.text("state.current") })
        assertEquals(listOf(null, "A"), observed.map { it.text("state.previous") })
        assertEquals("A → B", observed.last().text("detail"))
    }

    @Test
    fun multipleCollectorsAreTrackedSeparately() = runTest {
        val journey = newJourney()
        val state = MutableStateFlow(1).traceState("s", FlowTraceOptions(journey = { journey }))
        val first = launch { state.collect {} }
        val second = launch { state.collect {} }
        runCurrent()
        first.cancelAndJoin()
        second.cancelAndJoin()
        val events = journey.events()
        val added = events.filter { it.text("state.event") == "collector-added" }
        assertEquals(2, added.size)
        assertEquals(2, added.map { it.text("collector.id") }.toSet().size)
        assertEquals(
            listOf(1L, 2L),
            added.map { (it.metadata["state.collectors"] as MetadataValue.Integer).value },
        )
        val removed = events.filter { it.text("state.event") == "collector-removed" }
        assertEquals(listOf(1L, 0L), removed.map { (it.metadata["state.collectors"] as MetadataValue.Integer).value })
        assertTrue(removed.all { it.text("state.end") == "cancelled" })
    }

    @Test
    fun tracedStateFlowKeepsValueAndReplayCache() = runTest {
        val source = MutableStateFlow(5)
        val traced = source.traceState("s")
        source.value = 6
        assertEquals(6, traced.value)
        assertEquals(listOf(6), traced.replayCache)
    }

    @Test
    fun sharedFlowDeliversEveryValueAndPreservesReplay() = runTest {
        val journey = newJourney()
        val shared = MutableSharedFlow<Int>(replay = 1, extraBufferCapacity = 8)
        val traced = shared.traceShared("events", FlowTraceOptions(journey = { journey }))
        val seen = mutableListOf<Int>()
        val job = launch { traced.collect { seen += it } }
        runCurrent()
        shared.emit(1)
        shared.emit(2)
        runCurrent()
        job.cancelAndJoin()
        assertEquals(listOf(1, 2), seen)
        assertEquals(listOf(2), traced.replayCache)
        assertEquals(
            listOf("collector-added", "observed", "observed", "collector-removed"),
            journey.events().states(),
        )
    }

    @Test
    fun eventsAttachToTheAmbientSpan() = runTest {
        val journey = newJourney()
        journey.traceSuspend("ui") {
            flowOf(1).traceFlow("numbers").toList()
        }
        val trace = checkNotNull(journey.snapshot())
        val span = trace.spans.single { it.name == "ui" }
        assertTrue(trace.events.all { it.spanId == span.id })
        assertTrue(trace.events.all { it.category == SpanCategory.STATE })
    }

    @Test
    fun noJourneyMeansNoRecordingAndNoChange() = runTest {
        val values = flowOf(1, 2).traceFlow("numbers").toList()
        assertEquals(listOf(1, 2), values)
    }

    @Test
    fun concurrentStreamsStayDistinct() = runTest {
        val journey = newJourney()
        journey.traceSuspend("ui") {
            kotlinx.coroutines.coroutineScope {
                listOf("a", "b").map { stream ->
                    async { flowOf(1, 2).onEach { }.traceFlow(stream).toList() }
                }.awaitAll()
            }
        }
        val events = journey.events()
        for (stream in listOf("a", "b")) {
            val own = events.filter { it.text("state.stream") == stream }
            assertEquals(6, own.size)
            assertEquals("started", own.first().text("state.event"))
            assertEquals("completed", own.last().text("state.event"))
        }
    }

    @Test
    fun defaultFormatterNeverCallsToString() = runTest {
        val journey = newJourney()
        class Secret {
            override fun toString(): String = error("must not be called")
        }
        journey.traceSuspend("ui") { flowOf(Secret()).traceFlow("secrets").toList() }
        val observed = journey.events().first { it.text("state.event") == "observed" }
        assertEquals("Secret", observed.text("detail"))
    }

    @Test
    fun throwingFormatterDoesNotAffectTheStream() = runTest {
        val journey = newJourney()
        val options = FlowTraceOptions<Int>(formatter = { error("bad formatter") })
        val values = journey.traceSuspend("ui") { flowOf(1, 2).traceFlow("n", options).toList() }
        assertEquals(listOf(1, 2), values)
        assertEquals("<unformattable>", journey.events().first { it.text("state.event") == "observed" }.text("detail"))
    }

    @Test
    fun summariesAreTruncated() = runTest {
        val journey = newJourney()
        val options = FlowTraceOptions<String>(formatter = { it }, maxSummaryLength = 5)
        journey.traceSuspend("ui") { flowOf("abcdefghij").traceFlow("n", options).toList() }
        assertEquals("abcde", journey.events().first { it.text("state.event") == "observed" }.text("detail"))
    }

    @Test
    fun lifecycleStatesAreRecordedForTheCollector() = runTest {
        val journey = newJourney()
        val lifecycle = MutableStateFlow(CollectorLifecycleState.Created)
        val options = FlowTraceOptions<Int>(journey = { journey }, lifecycle = { lifecycle })
        val job = launch { MutableStateFlow(1).observeWithJourneyScope("s", options) }
        runCurrent()
        lifecycle.value = CollectorLifecycleState.Started
        runCurrent()
        job.cancelAndJoin()
        val states = journey.events().filter { it.category == SpanCategory.LIFECYCLE }.map { it.text("lifecycle.state") }
        assertEquals(listOf("Created", "Started"), states)
    }

    @Test
    fun launchedBoundaryRecordsCompletionCancellationAndFailure() = runTest {
        val journey = newJourney()
        journey.traceSuspend("ui") {
            kotlinx.coroutines.coroutineScope {
                launchTraced("ok") { }.join()
                val gate = Channel<Unit>()
                val hung = launchTraced("hung") { gate.receive() }
                runCurrent()
                hung.cancelAndJoin()
                val swallow = kotlinx.coroutines.CoroutineExceptionHandler { _, _ -> }
                launchTraced("bad", context = kotlinx.coroutines.SupervisorJob() + swallow) { error("x") }.join()
            }
        }
        advanceUntilIdle()
        val trace = checkNotNull(journey.snapshot())
        val ui = trace.spans.single { it.name == "ui" }
        assertEquals(SpanStatus.OK, trace.spans.single { it.name == "ok" }.status)
        assertEquals(SpanStatus.CANCELLED, trace.spans.single { it.name == "hung" }.status)
        assertEquals(SpanStatus.FAILED, trace.spans.single { it.name == "bad" }.status)
        assertTrue(listOf("ok", "hung", "bad").all { name -> trace.spans.single { it.name == name }.parentId == ui.id })
    }

    @Test
    fun asyncBoundaryReturnsTheValue() = runTest {
        val journey = newJourney()
        val result = journey.traceSuspend("ui") {
            kotlinx.coroutines.coroutineScope { asyncTraced("calc") { 21 * 2 }.await() }
        }
        assertEquals(42, result)
        assertEquals(SpanStatus.OK, checkNotNull(journey.snapshot()).spans.single { it.name == "calc" }.status)
    }

    private suspend fun awaitCancellationForever(): Nothing = kotlinx.coroutines.awaitCancellation()
}
