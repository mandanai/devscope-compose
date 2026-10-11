@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package dev.chayaphon.journeyscope.export.storage

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile

public actual fun createTraceFileSystem(directory: String): TraceFileSystem = IosTraceFileSystem(directory)

/** Application Support is not user-visible; the store additionally excludes it from backups. */
public fun iosTraceDirectory(): String {
    val roots = NSFileManager.defaultManager.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask)
    val root = (roots.firstOrNull() as? NSURL)?.path ?: error("Application Support directory unavailable")
    return "$root/journeyscope"
}

internal class IosTraceFileSystem(
    private val directory: String,
) : TraceFileSystem {
    private val manager = NSFileManager.defaultManager

    init {
        ensureDirectory()
    }

    override fun list(): List<StoredFile> {
        val names = manager.contentsOfDirectoryAtPath(directory, null) ?: return emptyList()
        return names.mapNotNull { it as? String }.mapNotNull { name ->
            val attributes = manager.attributesOfItemAtPath("$directory/$name", null) ?: return@mapNotNull null
            val size = (attributes[NSFileSize] as? NSNumber)?.longLongValue ?: 0L
            val modified = (attributes[NSFileModificationDate] as? NSDate)?.timeIntervalSince1970?.times(MILLIS)?.toLong() ?: 0L
            StoredFile(name, size, modified)
        }
    }

    override fun read(name: String): String? = NSString.stringWithContentsOfFile(path(name), NSUTF8StringEncoding, null)

    override fun writeAtomically(
        name: String,
        content: String,
    ) {
        ensureDirectory()
        val written = NSString.create(string = content).writeToFile(path(name), true, NSUTF8StringEncoding, null)
        if (!written) error("write failed")
        manager.setAttributes(mapOf(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication), path(name), null)
    }

    override fun delete(name: String): Boolean = manager.removeItemAtPath(path(name), null)

    private fun ensureDirectory() {
        if (manager.fileExistsAtPath(directory)) return
        manager.createDirectoryAtPath(
            directory,
            true,
            mapOf(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication),
            null,
        )
        NSURL.fileURLWithPath(directory).setResourceValue(true, NSURLIsExcludedFromBackupKey, null)
    }

    private fun path(name: String): String {
        require(name.isNotEmpty() && '/' !in name && '\\' !in name && name != "." && name != "..") { "invalid file name" }
        return "$directory/$name"
    }

    private companion object {
        const val MILLIS = 1000.0
    }
}
