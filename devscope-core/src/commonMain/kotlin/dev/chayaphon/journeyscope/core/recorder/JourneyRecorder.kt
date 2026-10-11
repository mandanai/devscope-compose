package dev.chayaphon.journeyscope.core.recorder

import dev.chayaphon.journeyscope.core.model.JourneyTrace
import dev.chayaphon.journeyscope.core.model.TraceId
import kotlinx.coroutines.flow.SharedFlow

/** Engine-side view of a trace owned by a recorder. */
public interface RecordedTrace {
    public val id: TraceId
    public val isFinished: Boolean

    public fun snapshot(): JourneyTrace

    public fun interrupt(reason: String)

    /** Called when the recorder drops this trace; further writes must be ignored. */
    public fun detach()
}

/** Change notification; consumers may miss some and should re-read [JourneyRecorder.snapshot]. */

public sealed class RecorderEvent {
    public data class TraceAttached(
        val id: TraceId,
    ) : RecorderEvent()

    public data class TraceChanged(
        val id: TraceId,
    ) : RecorderEvent()

    public data class TraceFinished(
        val trace: JourneyTrace,
    ) : RecorderEvent()

    /** Emitted when a trace is dropped to stay within capacity. */

    public data class TraceEvicted(
        val id: TraceId,
    ) : RecorderEvent()

    public data object Cleared : RecorderEvent()
}

/** Holds traces and publishes change events; called from arbitrary threads, so implementations must be thread-safe. */

public interface JourneyRecorder {
    /** Never suspends writers; slow collectors lose the oldest events and should re-read [snapshot]. */
    public val events: SharedFlow<RecorderEvent>

    public fun attach(trace: RecordedTrace)

    public fun notifyChanged(id: TraceId)

    public fun notifyFinished(trace: JourneyTrace)

    public fun snapshot(): List<JourneyTrace>

    public fun find(id: TraceId): JourneyTrace?

    /** Traces that have not reached a terminal state. */

    public fun liveTraces(): List<RecordedTrace>

    public fun clear()
}
