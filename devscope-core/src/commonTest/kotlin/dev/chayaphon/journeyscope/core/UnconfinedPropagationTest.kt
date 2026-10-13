package dev.chayaphon.journeyscope.core

import dev.chayaphon.journeyscope.core.model.SpanCategory
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UnconfinedPropagationTest {
    @Test
    fun nestedSpanKeepsParentAcrossUnconfinedHop() =
        runTest {
            val journey = testTracer().startJourney("J")
            journey.traceSuspend("outer", SpanCategory.VIEW_MODEL) {
                withContext(Dispatchers.Unconfined) {
                    journey.traceSuspend("inner", SpanCategory.USE_CASE) {}
                }
            }
            journey.finish()

            val trace = journey.trace()
            assertEquals(trace.span("outer").id, trace.span("inner").parentId)
        }

    @Test
    fun contextSurvivesResumptionOnAnotherDispatcher() =
        runTest {
            val journey = testTracer().startJourney("J")
            journey.traceSuspend("outer", SpanCategory.VIEW_MODEL) {
                withContext(Dispatchers.Unconfined) {
                    assertNotNull(currentTraceContext())
                    withContext(Dispatchers.Default) { yield() }
                    assertNotNull(currentTraceContext())
                    checkNotNull(currentTraceContext()).beginChildSpan("after-hop", SpanCategory.REPOSITORY).end()
                }
            }
            journey.finish()

            val trace = journey.trace()
            assertEquals(trace.span("outer").id, trace.span("after-hop").parentId)
        }

    @Test
    fun concurrentUnconfinedBranchesKeepTheirOwnParents() =
        runTest {
            val journey = testTracer().startJourney("J")
            withContext(Dispatchers.Unconfined) {
                listOf("a", "b")
                    .map { name ->
                        async(start = CoroutineStart.UNDISPATCHED) {
                            journey.traceSuspend(name, SpanCategory.USE_CASE) {
                                yield()
                                journey.traceSuspend("$name-child", SpanCategory.REPOSITORY) {}
                            }
                        }
                    }.awaitAll()
            }
            journey.finish()

            val trace = journey.trace()
            assertEquals(trace.span("a").id, trace.span("a-child").parentId)
            assertEquals(trace.span("b").id, trace.span("b-child").parentId)
        }

    @Test
    fun launchOnUnconfinedNeedsExplicitJourneyContext() =
        runTest {
            val journey = testTracer().startJourney("J")
            var plain: TraceContext? = null
            var bound: TraceContext? = null
            withContext(Dispatchers.Unconfined) {
                launch { plain = currentTraceContext() }.join()
                launchInJourney(journey, context = Dispatchers.Unconfined) { bound = currentTraceContext() }.join()
            }

            assertNull(plain)
            assertNotNull(bound)
        }
}
