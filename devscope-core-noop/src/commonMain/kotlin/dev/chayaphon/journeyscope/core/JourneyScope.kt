package dev.chayaphon.journeyscope.core

import dev.chayaphon.journeyscope.core.model.DiagnosticMetadata

/** Release-build twin of the real `JourneyScope`: same call-site API, no collection. */
public object JourneyScope {
    public const val VERSION: String = "0.1.0-SNAPSHOT"

    private val inert: JourneyTracer = JourneyTracer(JourneyScopeConfig())

    public val tracer: JourneyTracer get() = inert

    public val isEnabled: Boolean get() = false

    /** Ignores [config]; collection stays off in this artifact. */

    public fun configure(config: JourneyScopeConfig): JourneyTracer = inert

    public fun disable() {
        // Nothing is ever enabled in this artifact.
    }

    public fun startJourney(
        name: String,
        metadata: DiagnosticMetadata = DiagnosticMetadata.Empty,
    ): Journey = inert.startJourney(name, metadata)

    public suspend fun <T> withJourney(
        name: String,
        metadata: DiagnosticMetadata = DiagnosticMetadata.Empty,
        block: suspend Journey.() -> T,
    ): T = inert.withJourney(name, metadata, block)

    public fun interruptActive(reason: String) {
        inert.interruptActive(reason)
    }
}
