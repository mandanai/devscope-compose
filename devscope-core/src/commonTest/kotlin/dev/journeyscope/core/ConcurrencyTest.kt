package dev.journeyscope.core

import dev.journeyscope.core.model.SpanCategory
import dev.journeyscope.core.model.TraceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConcurrencyTest {
    @Test
    fun parallelEventsOnOneJourneyAreAllRecordedWithUniqueIds() =
        runTest {
            val tracer = testTracer(JourneyScopeConfig(enabled = true, maxEventsPerTrace = EVENTS_PER_WORKER * WORKERS * 2))
            val journey = tracer.startJourney("parallel")
            withContext(Dispatchers.Default) {
                (1..WORKERS)
                    .map { worker ->
                        async { repeat(EVENTS_PER_WORKER) { journey.recordEvent("w$worker-$it", SpanCategory.CUSTOM) } }
                    }.awaitAll()
            }
            journey.finish()

            val trace = journey.trace()
            assertEquals(WORKERS * EVENTS_PER_WORKER, trace.events.size)
            assertEquals(
                trace.events.size,
                trace.events
                    .map { it.id }
                    .toSet()
                    .size,
            )
        }

    @Test
    fun parallelSpansAllEnd() =
        runTest {
            val tracer = testTracer(JourneyScopeConfig(enabled = true, maxEventsPerTrace = SPANS * 4))
            val journey = tracer.startJourney("spans")
            withContext(Dispatchers.Default) {
                (1..SPANS)
                    .map { index -> async { journey.traceSuspend("span-$index", SpanCategory.USE_CASE) { index } } }
                    .awaitAll()
            }
            journey.finish()

            val trace = journey.trace()
            assertEquals(SPANS, trace.spans.size)
            assertTrue(trace.spans.none { it.isOpen })
        }

    @Test
    fun recorderStaysBoundedUnderParallelJourneyCreation() =
        runTest {
            val config = JourneyScopeConfig(enabled = true, maxTraces = MAX_TRACES)
            val tracer = testTracer(config)
            withContext(Dispatchers.Default) {
                (1..JOURNEYS)
                    .map { index ->
                        async {
                            tracer.startJourney("journey-$index").also {
                                it.recordEvent("tap", SpanCategory.UI_INTERACTION)
                                it.finish()
                            }
                        }
                    }.awaitAll()
            }

            val stored = tracer.recorder.snapshot()
            assertTrue(stored.size <= MAX_TRACES, "stored ${stored.size} > $MAX_TRACES")
            assertTrue(stored.all { it.status == TraceStatus.COMPLETED })
        }

    private companion object {
        const val WORKERS = 16
        const val EVENTS_PER_WORKER = 200
        const val SPANS = 300
        const val MAX_TRACES = 20
        const val JOURNEYS = 500
    }
}
