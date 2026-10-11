package dev.chayaphon.journeyscope.export.persistence

import dev.chayaphon.journeyscope.core.JourneyScope
import dev.chayaphon.journeyscope.core.JourneyScopeConfig
import dev.chayaphon.journeyscope.core.recorder.InMemoryJourneyRecorder
import dev.chayaphon.journeyscope.core.recorder.JourneyRecorder
import dev.chayaphon.journeyscope.core.timing.JourneyClock
import dev.chayaphon.journeyscope.core.timing.SystemJourneyClock
import dev.chayaphon.journeyscope.export.policy.ExportPolicy
import dev.chayaphon.journeyscope.export.storage.StorageListener
import dev.chayaphon.journeyscope.export.storage.TraceFileSystem
import dev.chayaphon.journeyscope.export.storage.TraceStore

/** The recorder (and optional store) produced by [install]; persistence is opt-in via [ExportPolicy.persistenceEnabled]. */
public class JourneyPersistence internal constructor(
    public val recorder: JourneyRecorder,
    public val store: TraceStore?,
    private val persisting: PersistingJourneyRecorder?,
) {
    /** False when the SDK or persistence is disabled and traces stay in memory. */
    public val isPersisting: Boolean get() = persisting != null

    /** Waits for queued traces to be written; no-op when not persisting. */
    public suspend fun flush() {
        persisting?.flush()
    }

    /** Stops background persistence; idempotent. */
    public fun close() {
        persisting?.close()
    }

    public companion object {
        /**
         * Returns the recorder to pass to `JourneyScope.configure`. Unless both the SDK and persistence are
         * enabled this is a plain in-memory recorder and [fileSystem] is never invoked.
         */
        public fun install(
            config: JourneyScopeConfig,
            policy: ExportPolicy,
            fileSystem: () -> TraceFileSystem,
            listener: StorageListener = StorageListener.None,
            isEnabled: () -> Boolean = { JourneyScope.isEnabled },
            clock: JourneyClock = SystemJourneyClock,
        ): JourneyPersistence {
            val memory = InMemoryJourneyRecorder(config.maxTraces)
            if (!config.enabled || !policy.persistenceEnabled) return JourneyPersistence(memory, null, null)
            val store = TraceStore(fileSystem(), policy, clock, listener)
            val persisting = PersistingJourneyRecorder(memory, store, policy.queueCapacity, listener, isEnabled = isEnabled)
            return JourneyPersistence(persisting, store, persisting)
        }
    }
}
