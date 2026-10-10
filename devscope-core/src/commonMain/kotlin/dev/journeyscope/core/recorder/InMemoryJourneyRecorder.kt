package dev.journeyscope.core.recorder

import dev.journeyscope.core.model.JourneyTrace
import dev.journeyscope.core.model.TraceId
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** Bounded ring of traces; each trace owns its own state, so this only tracks membership. */
public class InMemoryJourneyRecorder(
    private val maxTraces: Int = DEFAULT_MAX_TRACES,
) : JourneyRecorder {
    // Plain class on purpose: StateFlow CAS compares with equals, identity is what we want.
    private class Ring(
        val traces: List<RecordedTrace>,
    )

    private val ring = MutableStateFlow(Ring(emptyList()))
    private val mutableEvents =
        MutableSharedFlow<RecorderEvent>(
            extraBufferCapacity = EVENT_BUFFER,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    init {
        require(maxTraces > 0) { "maxTraces must be positive" }
    }

    override val events: SharedFlow<RecorderEvent> get() = mutableEvents

    /** Emits the full snapshot now and after every change; conflation-safe for UI collectors. */
    public fun observeTraces(): Flow<List<JourneyTrace>> = mutableEvents.onStart { emit(RecorderEvent.Cleared) }.map { snapshot() }

    override fun attach(trace: RecordedTrace) {
        val evicted = ArrayList<RecordedTrace>()
        while (true) {
            val current = ring.value
            val next = ArrayList(current.traces).apply { add(trace) }
            val dropped = ArrayList<RecordedTrace>()
            while (next.size > maxTraces) dropped += next.removeAt(oldestEvictableIndex(next))
            if (ring.compareAndSet(current, Ring(next))) {
                evicted += dropped
                break
            }
        }
        evicted.forEach {
            it.detach()
            mutableEvents.tryEmit(RecorderEvent.TraceEvicted(it.id))
        }
        mutableEvents.tryEmit(RecorderEvent.TraceAttached(trace.id))
    }

    override fun notifyChanged(id: TraceId) {
        mutableEvents.tryEmit(RecorderEvent.TraceChanged(id))
    }

    override fun notifyFinished(trace: JourneyTrace) {
        mutableEvents.tryEmit(RecorderEvent.TraceFinished(trace))
    }

    override fun snapshot(): List<JourneyTrace> = ring.value.traces.map { it.snapshot() }

    override fun find(id: TraceId): JourneyTrace? =
        ring.value.traces
            .firstOrNull { it.id == id }
            ?.snapshot()

    override fun liveTraces(): List<RecordedTrace> = ring.value.traces.filter { !it.isFinished }

    override fun clear() {
        val removed = ring.getAndUpdate { Ring(emptyList()) }.traces
        removed.forEach { it.detach() }
        mutableEvents.tryEmit(RecorderEvent.Cleared)
    }

    private fun oldestEvictableIndex(traces: List<RecordedTrace>): Int = traces.indexOfFirst { it.isFinished }.takeIf { it >= 0 } ?: 0

    public companion object {
        public const val DEFAULT_MAX_TRACES: Int = 50
        private const val EVENT_BUFFER = 256
    }
}
