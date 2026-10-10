package dev.journeyscope.ui.state

import dev.journeyscope.core.model.JourneyTrace
import dev.journeyscope.core.recorder.JourneyRecorder
import dev.journeyscope.core.recorder.RecorderEvent
import dev.journeyscope.core.timing.JourneyClock
import dev.journeyscope.core.timing.SystemJourneyClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** A consistent snapshot of traces; [nowNanos] is the clock reading used to measure unfinished work. */
public data class TraceBatch(val traces: List<JourneyTrace>, val nowNanos: Long)

public fun interface JourneyTraceSource {
    public fun observe(): Flow<TraceBatch>
}

public fun JourneyRecorder.asTraceSource(
    clock: JourneyClock = SystemJourneyClock,
    tickInterval: Duration = DEFAULT_TICK,
): JourneyTraceSource = JourneyTraceSource { observeBatches(clock, tickInterval) }

public fun staticTraceSource(traces: List<JourneyTrace>, nowNanos: Long): JourneyTraceSource =
    JourneyTraceSource { flowOf(TraceBatch(traces, nowNanos)) }

private val DEFAULT_TICK = 250.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
private fun JourneyRecorder.observeBatches(clock: JourneyClock, tick: Duration): Flow<TraceBatch> =
    events
        .onStart { emit(RecorderEvent.Cleared) }
        .map { snapshot() }
        .conflate()
        .transformLatest { traces ->
            emit(TraceBatch(traces, clock.monotonicNanos()))
            if (traces.any { !it.status.isTerminal }) {
                while (true) {
                    delay(tick)
                    emit(TraceBatch(traces, clock.monotonicNanos()))
                }
            }
        }
