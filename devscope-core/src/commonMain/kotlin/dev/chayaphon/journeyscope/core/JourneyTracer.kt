package dev.chayaphon.journeyscope.core

import dev.chayaphon.journeyscope.core.model.DiagnosticMetadata
import dev.chayaphon.journeyscope.core.model.TraceId
import dev.chayaphon.journeyscope.core.recorder.InMemoryJourneyRecorder
import dev.chayaphon.journeyscope.core.recorder.JourneyRecorder
import dev.chayaphon.journeyscope.core.timing.JourneyClock
import dev.chayaphon.journeyscope.core.timing.SystemJourneyClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Creates journeys under one fixed [JourneyScopeConfig] and records them into [recorder].
 * It never throws into application code: recorder faults are swallowed.
 */

public class JourneyTracer(
    public val config: JourneyScopeConfig = JourneyScopeConfig(),
    public val recorder: JourneyRecorder = InMemoryJourneyRecorder(config.maxTraces),
    clock: JourneyClock = SystemJourneyClock,
    idGenerator: IdGenerator = RandomPrefixIdGenerator(),
    private val random: Random = Random.Default,
) {
    private val deps = EngineDeps(config, clock, recorder, idGenerator)

    /** Returns a CREATED journey, or an inert one when disabled or sampled out by `samplingRate`. */

    public fun createJourney(
        name: String,
        metadata: DiagnosticMetadata = DiagnosticMetadata.Empty,
    ): Journey {
        if (!config.enabled || !isSampled()) return NoopJourney
        val journey =
            RecordingJourney(
                id = TraceId(deps.newId("t")),
                name = deps.sanitizer.label(name),
                deps = deps,
                metadata = deps.sanitizer.metadata(metadata),
            )
        safely { recorder.attach(journey) }
        return journey
    }

    /** Creates a journey and starts it immediately. */

    public fun startJourney(
        name: String,
        metadata: DiagnosticMetadata = DiagnosticMetadata.Empty,
    ): Journey = createJourney(name, metadata).also { it.start() }

    /** Runs [block] in a journey, finishing it as COMPLETED, CANCELLED or FAILED and rethrowing any exception. */
    public suspend fun <T> withJourney(
        name: String,
        metadata: DiagnosticMetadata = DiagnosticMetadata.Empty,
        block: suspend Journey.() -> T,
    ): T {
        val journey = startJourney(name, metadata)
        if (!journey.isRecording) return journey.block()
        try {
            val result = withContext(journey.context()) { journey.block() }
            journey.finish()
            return result
        } catch (cancellation: CancellationException) {
            journey.cancel()
            throw cancellation
        } catch (t: Throwable) {
            journey.fail(t)
            throw t
        }
    }

    /** For host lifecycle hooks (app backgrounded or killed): running journeys become INTERRUPTED, never COMPLETED. */
    public fun interruptActive(reason: String) {
        safely { recorder.liveTraces().forEach { trace -> safely { trace.interrupt(reason) } } }
    }

    private fun isSampled(): Boolean = config.samplingRate >= 1.0 || random.nextDouble() < config.samplingRate
}
