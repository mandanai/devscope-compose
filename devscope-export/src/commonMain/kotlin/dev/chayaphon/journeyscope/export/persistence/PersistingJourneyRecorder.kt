package dev.chayaphon.journeyscope.export.persistence

import dev.chayaphon.journeyscope.core.JourneyScope
import dev.chayaphon.journeyscope.core.model.JourneyTrace
import dev.chayaphon.journeyscope.core.recorder.JourneyRecorder
import dev.chayaphon.journeyscope.export.storage.StorageEvent
import dev.chayaphon.journeyscope.export.storage.StorageListener
import dev.chayaphon.journeyscope.export.storage.TraceStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Persists finished traces off the caller's thread through a bounded queue. Writers never block: when the
 * queue is full the oldest pending trace is dropped.
 */
public class PersistingJourneyRecorder(
    private val delegate: JourneyRecorder,
    private val store: TraceStore,
    queueCapacity: Int,
    private val listener: StorageListener = StorageListener.None,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val isEnabled: () -> Boolean = { JourneyScope.isEnabled },
) : JourneyRecorder by delegate {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val queue = Channel<JourneyTrace>(queueCapacity, BufferOverflow.DROP_OLDEST)
    private val capacity = queueCapacity
    private val pending = MutableStateFlow(0)
    private val closed = MutableStateFlow(false)

    init {
        require(queueCapacity > 0) { "queueCapacity must be positive" }
        scope.launch {
            for (trace in queue) {
                try {
                    store.saveTrace(trace)
                } finally {
                    pending.update { (it - 1).coerceAtLeast(0) }
                }
            }
        }
    }

    override fun notifyFinished(trace: JourneyTrace) {
        delegate.notifyFinished(trace)
        if (closed.value || !isEnabled() || store.isSuspended) return
        if (pending.value >= capacity) listener.onEvent(StorageEvent.QueueOverflow) else pending.update { it + 1 }
        queue.trySend(trace)
    }

    /** Waits until everything queued so far has been written; for shutdown hooks and tests. */
    public suspend fun flush() {
        pending.first { it == 0 }
    }

    /** Cancels the writer and discards pending traces; idempotent. */
    public fun close() {
        if (!closed.compareAndSet(expect = false, update = true)) return
        queue.close()
        scope.cancel()
    }
}
