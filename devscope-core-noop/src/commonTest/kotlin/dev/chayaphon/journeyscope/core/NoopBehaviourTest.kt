package dev.chayaphon.journeyscope.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NoopBehaviourTest {
    @Test
    fun neverEnablesAndBlocksStillReturnTheirResults() =
        runTest {
            JourneyScope.configure(JourneyScopeConfig(enabled = true))
            assertFalse(JourneyScope.isEnabled)
            val journey = JourneyScope.startJourney("x")
            assertFalse(journey.isRecording)
            assertEquals(7, journey.trace("a") { 7 })
            assertEquals(9, JourneyScope.withJourney("y") { traceSuspend("b") { 9 } })
        }
}
