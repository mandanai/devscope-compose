package dev.journeyscope.export.storage

public sealed class StorageEvent {
    public data class Failure(val operation: String, val errorType: String) : StorageEvent()
    public data class CorruptFile(val name: String) : StorageEvent()
    public data class Evicted(val count: Int) : StorageEvent()
    public data object QueueOverflow : StorageEvent()
    public data object CircuitOpened : StorageEvent()
}

public fun interface StorageListener {
    public fun onEvent(event: StorageEvent)

    public companion object {
        public val None: StorageListener = StorageListener { }
    }
}

public enum class SaveOutcome { SAVED, TOO_LARGE, INVALID_ID, FAILED, SUSPENDED }

public data class TraceQuery(
    val statuses: Set<dev.journeyscope.core.model.TraceStatus>? = null,
    val nameContains: String? = null,
    val sinceWallMillis: Long? = null,
    val limit: Int = Int.MAX_VALUE,
)
