package dev.journeyscope.core

import dev.journeyscope.core.model.DiagnosticMetadata

/** Release-build tracer that only hands out the shared inert journey. */

public class JourneyTracer(
    public val config: JourneyScopeConfig = JourneyScopeConfig(),
) {
    public fun createJourney(
        name: String,
        metadata: DiagnosticMetadata = DiagnosticMetadata.Empty,
    ): Journey = Journey.INERT

    public fun startJourney(
        name: String,
        metadata: DiagnosticMetadata = DiagnosticMetadata.Empty,
    ): Journey = Journey.INERT

    public suspend fun <T> withJourney(
        name: String,
        metadata: DiagnosticMetadata = DiagnosticMetadata.Empty,
        block: suspend Journey.() -> T,
    ): T = Journey.INERT.block()

    public fun interruptActive(reason: String) {
        // No journeys exist.
    }
}
