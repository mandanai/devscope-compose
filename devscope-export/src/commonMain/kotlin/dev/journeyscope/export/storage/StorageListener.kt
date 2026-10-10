package dev.journeyscope.export.storage

/** Diagnostics about persistence health; carries error types and names only, never trace content. */
public sealed class StorageEvent {
    public data class Failure(
        val operation: String,
        val errorType: String,
    ) : StorageEvent()

    public data class CorruptFile(
        val name: String,
    ) : StorageEvent()

    public data class Evicted(
        val count: Int,
    ) : StorageEvent()

    public data object QueueOverflow : StorageEvent()

    /** Repeated failures suspended persistence until [TraceStore.resume]. */
    public data object CircuitOpened : StorageEvent()
}

/** Receives storage events on the thread where they occur; must not throw or block. */
public fun interface StorageListener {
    public fun onEvent(event: StorageEvent)

    public companion object {
        public val None: StorageListener = StorageListener { }
    }
}

/** Result of [TraceStore.saveTrace]; failures are reported, not thrown. */
public enum class SaveOutcome { SAVED, TOO_LARGE, INVALID_ID, FAILED, SUSPENDED }

/** Filters for [TraceStore.query]; all conditions must match. */
public data class TraceQuery(
    val statuses: Set<dev.journeyscope.core.model.TraceStatus>? = null,
    val nameContains: String? = null,
    val sinceWallMillis: Long? = null,
    val limit: Int = Int.MAX_VALUE,
)
