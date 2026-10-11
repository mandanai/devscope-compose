package dev.chayaphon.journeyscope.export.storage

/** A file in a [TraceFileSystem] with its size and last-modified time in epoch milliseconds. */
public data class StoredFile(
    val name: String,
    val sizeBytes: Long,
    val modifiedMillis: Long,
)

/** Flat directory of small text files; implementations must make [writeAtomically] all-or-nothing. */
public interface TraceFileSystem {
    public fun list(): List<StoredFile>

    public fun read(name: String): String?

    public fun writeAtomically(
        name: String,
        content: String,
    )

    public fun delete(name: String): Boolean
}

/** Platform-protected directory: no-backup/private on Android, excluded-from-backup and file-protected on iOS. */
public expect fun createTraceFileSystem(directory: String): TraceFileSystem
