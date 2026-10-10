package dev.journeyscope.export.storage

import android.content.Context
import java.io.File
import java.io.IOException

public actual fun createTraceFileSystem(directory: String): TraceFileSystem = AndroidTraceFileSystem(File(directory))

/** `noBackupFilesDir` is private to the app and excluded from Auto Backup and device-to-device transfer. */
public fun androidTraceDirectory(context: Context): String = File(context.noBackupFilesDir, "journeyscope").path

internal class AndroidTraceFileSystem(
    private val directory: File,
) : TraceFileSystem {
    init {
        directory.mkdirs()
    }

    override fun list(): List<StoredFile> =
        directory
            .listFiles()
            .orEmpty()
            .filter { it.isFile }
            .map { StoredFile(it.name, it.length(), it.lastModified()) }

    override fun read(name: String): String? = resolve(name).takeIf { it.isFile }?.readText(Charsets.UTF_8)

    override fun writeAtomically(
        name: String,
        content: String,
    ) {
        directory.mkdirs()
        val target = resolve(name)
        val temp = File(directory, name + TraceStore.TEMP_SUFFIX)
        try {
            temp.writeText(content, Charsets.UTF_8)
            if (!temp.renameTo(target)) throw IOException("rename failed")
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    override fun delete(name: String): Boolean = resolve(name).delete()

    private fun resolve(name: String): File {
        require(name.isNotEmpty() && '/' !in name && '\\' !in name && name != "." && name != "..") { "invalid file name" }
        return File(directory, name)
    }
}
