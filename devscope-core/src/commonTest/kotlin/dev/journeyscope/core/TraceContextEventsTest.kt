package dev.journeyscope.core

import dev.journeyscope.core.model.SpanCategory
import dev.journeyscope.core.model.SpanStatus
import dev.journeyscope.core.model.diagnosticMetadata
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TraceContextEventsTest {
    @Test
    fun eventsAttachToAmbientSpan() =
        runTest {
            val journey = testTracer().startJourney("J")
            journey.traceSuspend("outer", SpanCategory.VIEW_MODEL) {
                currentTraceContext()!!.recordEvent("tick", SpanCategory.STATE, diagnosticMetadata { put("k", "v") })
            }
            journey.finish()
            val trace = journey.trace()
            val event = trace.events.single { it.name == "tick" }
            assertEquals(trace.span("outer").id, event.spanId)
            assertEquals(SpanCategory.STATE, event.category)
        }

    @Test
    fun childSpanIsParentedToAmbientSpan() =
        runTest {
            val journey = testTracer().startJourney("J")
            journey.traceSuspend("outer", SpanCategory.VIEW_MODEL) {
                currentTraceContext()!!.beginChildSpan("child", SpanCategory.NAVIGATION).end()
            }
            journey.finish()
            val trace = journey.trace()
            val child = trace.span("child")
            assertEquals(trace.span("outer").id, child.parentId)
            assertEquals(SpanStatus.OK, child.status)
            assertNotNull(child.endNanos)
        }
}
