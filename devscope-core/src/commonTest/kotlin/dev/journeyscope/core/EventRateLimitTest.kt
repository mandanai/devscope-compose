package dev.journeyscope.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class EventRateLimitTest {
    @Test
    fun dropsEventsOverTheLimitWithinOneSecondAndCountsThem() {
        val clock = FakeJourneyClock()
        val tracer = testTracer(JourneyScopeConfig(enabled = true, maxEventsPerSecond = 3), clock)
        val journey = tracer.startJourney("burst")
        repeat(5) { journey.recordEvent("e$it") }
        journey.finish()
        val trace = journey.trace()
        assertEquals(3, trace.events.size)
        assertEquals(2, trace.droppedItems)
    }

    @Test
    fun admitsAgainOnceTheWindowRollsOver() {
        val clock = FakeJourneyClock()
        val tracer = testTracer(JourneyScopeConfig(enabled = true, maxEventsPerSecond = 2), clock)
        val journey = tracer.startJourney("window")
        repeat(3) { journey.recordEvent("a$it") }
        clock.advance(1.seconds + 1.milliseconds)
        repeat(2) { journey.recordEvent("b$it") }
        journey.finish()
        assertEquals(4, journey.trace().events.size)
    }
}
