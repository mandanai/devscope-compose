package dev.journeyscope.core.recorder

import dev.journeyscope.core.model.JourneyTrace
import dev.journeyscope.core.model.TraceId
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

public sealed class RecorderEvent {
    public data class TraceAttached(val id: TraceId) : RecorderEvent()
    public data class TraceChanged(val id: TraceId) : RecorderEvent()
    public data class TraceFinished(val trace: JourneyTrace) : RecorderEvent()
    public data class TraceEvicted(val id: TraceId) : RecorderEvent()
    public data object Cleared : RecorderEvent()
}

public interface JourneyRecorder {
    /** Never suspends writers; slow collectors lose the oldest events and should re-read [snapshot]. */
    public val events: SharedFlow<RecorderEvent>

    public fun attach(trace: RecordedTrace)

    public fun notifyChanged(id: TraceId)

    public fun notifyFinished(trace: JourneyTrace)

    public fun snapshot(): List<JourneyTrace>

    public fun find(id: TraceId): JourneyTrace?

    public fun liveTraces(): List<RecordedTrace>

    public fun clear()
}
