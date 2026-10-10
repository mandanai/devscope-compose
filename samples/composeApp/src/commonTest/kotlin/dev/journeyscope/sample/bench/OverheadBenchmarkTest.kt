package dev.journeyscope.sample.bench

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OverheadBenchmarkTest {
    @Test
    fun disabledModeRecordsNothingAndCostsLessThanEnabled() {
        val results = OverheadBenchmark.run(journeys = 300, spansPerJourney = 3, warmup = 50).associateBy { it.mode }
        val disabled = results.getValue(BenchmarkMode.DISABLED)
        val enabled = results.getValue(BenchmarkMode.ENABLED)
        assertEquals(0, disabled.journeysRecorded)
        assertTrue(enabled.journeysRecorded > 0)
        assertTrue(
            disabled.nanosPerJourney < enabled.nanosPerJourney,
            "disabled=${disabled.nanosPerJourney}ns enabled=${enabled.nanosPerJourney}ns",
        )
        println(results.values.joinToString("\n") { "${it.mode}: ${it.nanosPerJourney} ns/journey" })
    }
}
