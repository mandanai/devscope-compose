package dev.journeyscope.sample.shop

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

enum class SampleScenario(val label: String) {
    Normal("Normal"),
    SlowApi("Slow API"),
    BusinessFailure("Business failure"),
    NetworkException("Network exception"),
    Cancelled("Cancelled"),
}

/** The only hardcoded durations in the sample: the simulated work itself. Recorded durations are always measured. */
data class LatencyProfile(
    val normal: Duration = 120.milliseconds,
    val slow: Duration = 2400.milliseconds,
    val database: Duration = 25.milliseconds,
    val cancelAfter: Duration = 300.milliseconds,
) {
    fun networkFor(scenario: SampleScenario): Duration = when (scenario) {
        SampleScenario.SlowApi, SampleScenario.Cancelled -> slow
        else -> normal
    }

    companion object {
        val Default = LatencyProfile()
    }
}
