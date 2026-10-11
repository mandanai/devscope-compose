package dev.chayaphon.journeyscope.core

import dev.chayaphon.journeyscope.core.model.DiagnosticMetadata
import dev.chayaphon.journeyscope.core.recorder.InMemoryJourneyRecorder
import dev.chayaphon.journeyscope.core.recorder.JourneyRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide entry point that owns the active [JourneyTracer]; collection is off until [configure] enables it.
 * Thread-safe: the active tracer is swapped atomically.
 */

public object JourneyScope {
    public const val VERSION: String = "0.1.0-SNAPSHOT"

    private val state = MutableStateFlow(JourneyTracer())

    private val current: JourneyTracer get() = state.value

    public val tracer: JourneyTracer get() = current

    /** Emits each tracer installed by [configure] or [disable], so tooling can follow reconfiguration. */
    public val tracerFlow: StateFlow<JourneyTracer> get() = state.asStateFlow()

    public val isEnabled: Boolean get() = current.config.enabled

    /** Collection stays off until the host calls this with `enabled = true` or [JourneyScopeConfig.debugOnly]. */
    public fun configure(
        config: JourneyScopeConfig,
        recorder: JourneyRecorder? = null,
    ): JourneyTracer {
        current.interruptActive(RECONFIGURED)
        return JourneyTracer(config, recorder ?: InMemoryJourneyRecorder(config.maxTraces)).also { state.value = it }
    }

    /** Turns collection off but keeps the recorder and the traces already captured. */

    public fun disable() {
        configure(current.config.copy(enabled = false), current.recorder)
    }

    /** Starts a journey on the active tracer; inert when disabled or sampled out. */

    public fun startJourney(
        name: String,
        metadata: DiagnosticMetadata = DiagnosticMetadata.Empty,
    ): Journey = current.startJourney(name, metadata)

    /** Runs [block] in a journey that is finished, cancelled or failed from its outcome; see [JourneyTracer.withJourney]. */

    public suspend fun <T> withJourney(
        name: String,
        metadata: DiagnosticMetadata = DiagnosticMetadata.Empty,
        block: suspend Journey.() -> T,
    ): T = current.withJourney(name, metadata, block)

    /** Marks running journeys INTERRUPTED; call from host lifecycle hooks. */

    public fun interruptActive(reason: String) {
        current.interruptActive(reason)
    }

    private const val RECONFIGURED = "JourneyScope reconfigured"
}
