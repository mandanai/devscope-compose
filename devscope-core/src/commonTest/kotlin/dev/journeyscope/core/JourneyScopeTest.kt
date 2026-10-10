package dev.journeyscope.core

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JourneyScopeTest {
    @AfterTest
    fun reset() {
        JourneyScope.isEnabled = false
    }

    @Test
    fun disabledByDefault() {
        assertFalse(JourneyScope.isEnabled)
    }

    @Test
    fun enableToggles() {
        JourneyScope.isEnabled = true
        assertTrue(JourneyScope.isEnabled)
    }

    @Test
    fun versionIsNotBlank() {
        assertTrue(JourneyScope.VERSION.isNotBlank())
    }
}
