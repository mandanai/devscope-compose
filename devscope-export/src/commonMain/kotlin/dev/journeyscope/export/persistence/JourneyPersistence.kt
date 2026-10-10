package dev.journeyscope.export.persistence

import dev.journeyscope.core.JourneyScope
import dev.journeyscope.core.JourneyScopeConfig
import dev.journeyscope.core.recorder.InMemoryJourneyRecorder
import dev.journeyscope.core.recorder.JourneyRecorder
import dev.journeyscope.core.timing.JourneyClock
import dev.journeyscope.core.timing.SystemJourneyClock
import dev.journeyscope.export.policy.ExportPolicy
import dev.journeyscope.export.storage.StorageListener
import dev.journeyscope.export.storage.TraceFileSystem
import dev.journeyscope.export.storage.TraceStore

public class JourneyPersistence internal constructor(
    public val recorder: JourneyRecorder,
    public val store: TraceStore?,
    private val persisting: PersistingJourneyRecorder?,
) {
    public val isPersisting: Boolean get() = persisting != null

    public suspend fun flush() {
        persisting?.flush()
    }

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
