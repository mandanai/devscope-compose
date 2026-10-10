package dev.journeyscope.ui.state

import dev.journeyscope.core.model.JourneyTrace
import dev.journeyscope.core.recorder.JourneyRecorder
import dev.journeyscope.core.recorder.RecorderEvent
import dev.journeyscope.core.timing.JourneyClock
import dev.journeyscope.core.timing.SystemJourneyClock
import dev.journeyscope.export.JourneyExportController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** A consistent snapshot of traces; [nowNanos] is the clock reading used to measure unfinished work. */
public data class TraceBatch(
    val traces: List<JourneyTrace>,
    val nowNanos: Long,
    val importedIds: Set<String> = emptySet(),
)

/** Supplies trace snapshots to the inspector; the flow should conflate so slow UI never backs up recording. */
public fun interface JourneyTraceSource {
    public fun observe(): Flow<TraceBatch>
}

/** Emits a new batch on each recorder change, and every [tickInterval] while any trace is unfinished so running durations stay current. */
public fun JourneyRecorder.asTraceSource(
    clock: JourneyClock = SystemJourneyClock,
    tickInterval: Duration = DEFAULT_TICK,
): JourneyTraceSource = JourneyTraceSource { observeBatches(clock, tickInterval) }

/** Emits a single fixed batch; for previews and tests. */
public fun staticTraceSource(
    traces: List<JourneyTrace>,
    nowNanos: Long,
): JourneyTraceSource = JourneyTraceSource { flowOf(TraceBatch(traces, nowNanos)) }

/** Adds persisted history and imported reports behind the live traces; live data always wins on id clashes. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun JourneyTraceSource.withExport(controller: JourneyExportController): JourneyTraceSource =
    JourneyTraceSource {
        controller.historyChanges.flatMapLatest {
            flow { emit(controller.history()) }.flatMapLatest { history ->
                combine(observe(), controller.importedTraces) { batch, imported ->
                    val liveIds = batch.traces.mapTo(HashSet()) { it.id }
                    val older = history.filter { it.id !in liveIds }
                    val olderIds = older.mapTo(HashSet()) { it.id }
                    val extra = imported.filter { it.id !in liveIds && it.id !in olderIds }
                    TraceBatch(older + extra + batch.traces, batch.nowNanos, extra.mapTo(HashSet()) { it.id.value })
                }
            }
        }
    }

private val DEFAULT_TICK = 250.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
private fun JourneyRecorder.observeBatches(
    clock: JourneyClock,
    tick: Duration,
): Flow<TraceBatch> =
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
