package dev.journeyscope.core

import dev.journeyscope.core.model.DiagnosticMetadata
import dev.journeyscope.core.recorder.InMemoryJourneyRecorder
import dev.journeyscope.core.recorder.JourneyRecorder
import kotlin.concurrent.Volatile

public object JourneyScope {
    public const val VERSION: String = "0.1.0-SNAPSHOT"

    @Volatile
    private var current: JourneyTracer = JourneyTracer()

    public val tracer: JourneyTracer get() = current

    public val isEnabled: Boolean get() = current.config.enabled

    /** Collection stays off until the host calls this with `enabled = true` or [JourneyScopeConfig.debugOnly]. */
    public fun configure(config: JourneyScopeConfig, recorder: JourneyRecorder? = null): JourneyTracer {
        current.interruptActive(RECONFIGURED)
        return JourneyTracer(config, recorder ?: InMemoryJourneyRecorder(config.maxTraces)).also { current = it }
    }

    public fun disable() {
        configure(current.config.copy(enabled = false), current.recorder)
    }

    public fun startJourney(name: String, metadata: DiagnosticMetadata = DiagnosticMetadata.Empty): Journey =
        current.startJourney(name, metadata)

    public suspend fun <T> withJourney(
        name: String,
        metadata: DiagnosticMetadata = DiagnosticMetadata.Empty,
        block: suspend Journey.() -> T,
    ): T = current.withJourney(name, metadata, block)

    public fun interruptActive(reason: String) {
        current.interruptActive(reason)
    }

    private const val RECONFIGURED = "JourneyScope reconfigured"
}
