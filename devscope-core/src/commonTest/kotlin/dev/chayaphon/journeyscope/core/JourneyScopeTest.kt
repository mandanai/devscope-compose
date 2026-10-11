package dev.chayaphon.journeyscope.core

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JourneyScopeTest {
    @AfterTest
    fun reset() {
        JourneyScope.configure(JourneyScopeConfig())
    }

    @Test
    fun disabledByDefault() {
        assertFalse(JourneyScope.isEnabled)
    }

    @Test
    fun configureEnablesAndDisableStops() {
        JourneyScope.configure(JourneyScopeConfig(enabled = true))
        assertTrue(JourneyScope.isEnabled)
        JourneyScope.disable()
        assertFalse(JourneyScope.isEnabled)
    }

    @Test
    fun debugOnlyFollowsHostFlag() {
        assertFalse(JourneyScopeConfig.debugOnly(isDebuggable = false).enabled)
        assertTrue(JourneyScopeConfig.debugOnly(isDebuggable = true).enabled)
    }

    @Test
    fun versionIsNotBlank() {
        assertTrue(JourneyScope.VERSION.isNotBlank())
    }

    @Test
    fun disabledScopeStartsInertJourneys() {
        assertFalse(JourneyScope.startJourney("noop").isRecording)
    }
}
