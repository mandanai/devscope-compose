package dev.journeyscope.export.storage

import dev.journeyscope.core.model.JourneyTrace
import dev.journeyscope.core.model.TraceId
import dev.journeyscope.core.spi.JourneyStore
import dev.journeyscope.core.timing.JourneyClock
import dev.journeyscope.core.timing.SystemJourneyClock
import dev.journeyscope.export.importing.ImportLimits
import dev.journeyscope.export.importing.ImportResult
import dev.journeyscope.export.importing.TraceImporter
import dev.journeyscope.export.policy.ExportPolicy
import dev.journeyscope.export.redaction.RedactionPipeline
import dev.journeyscope.export.schema.ReportContext
import dev.journeyscope.export.schema.TraceReportWriter
import dev.journeyscope.export.schema.TraceSchema
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Bounded history of redacted traces. Every write goes through [RedactionPipeline]; I/O errors never escape
 * (they are reported to [listener]) and repeated failures suspend persistence until [resume].
 */
public class TraceStore(
    private val fileSystem: TraceFileSystem,
    private val policy: ExportPolicy = ExportPolicy.Default,
    private val clock: JourneyClock = SystemJourneyClock,
    private val listener: StorageListener = StorageListener.None,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val failureThreshold: Int = DEFAULT_FAILURE_THRESHOLD,
) : JourneyStore {
    private val pipeline = RedactionPipeline(policy)
    private val importer = TraceImporter(
        limits = ImportLimits(maxBytes = policy.maxTraceBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), maxTraces = 1, maxSpansPerTrace = MAX_STORED_ITEMS, maxEventsPerTrace = MAX_STORED_ITEMS),
        redaction = pipeline,
    )
    private val mutex = Mutex()
    private var recovered = false
    private var consecutiveFailures = 0
    private var suspended = false

    public val isSuspended: Boolean get() = suspended

    public fun resume() {
        consecutiveFailures = 0
        suspended = false
    }

    public suspend fun saveTrace(trace: JourneyTrace): SaveOutcome {
        val safeId = trace.id.value.takeIf { SAFE_ID.matches(it) } ?: return SaveOutcome.INVALID_ID
        val redacted = pipeline.redact(trace)
        val content = TraceReportWriter.write(listOf(redacted), ReportContext(appVersion = policy.appVersion, exportedAtWallMillis = clock.wallClockMillis()))
        if (content.encodeToByteArray().size > policy.maxTraceBytes) return SaveOutcome.TOO_LARGE
        return guarded("save", SaveOutcome.FAILED) {
            mutex.withLock {
                if (suspended) return@withLock SaveOutcome.SUSPENDED
                recoverOnce()
                fileSystem.writeAtomically(fileName(trace.startWallMillis, safeId), content)
                enforceLimits()
                SaveOutcome.SAVED
            }
        }.also { outcome ->
            if (outcome == SaveOutcome.SAVED) consecutiveFailures = 0
        }
    }

    override suspend fun save(trace: JourneyTrace) {
        saveTrace(trace)
    }

    override suspend fun load(id: TraceId): JourneyTrace? {
        val safeId = id.value.takeIf { SAFE_ID.matches(it) } ?: return null
        return guarded("load", null) {
            mutex.withLock {
                recoverOnce()
                fileSystem.list().firstOrNull { it.name.endsWith("-$safeId.$EXTENSION") }?.let { readTrace(it.name) }
            }
        }
    }

    override suspend fun loadAll(): List<JourneyTrace> = query(TraceQuery())

    public suspend fun query(query: TraceQuery): List<JourneyTrace> = guarded("query", emptyList()) {
        mutex.withLock {
            recoverOnce()
            val result = ArrayList<JourneyTrace>()
            for (file in storedTraceFiles().sortedBy { it.name }) {
                if (result.size >= query.limit) break
                val trace = readTrace(file.name) ?: continue
                val matches = (query.statuses == null || trace.status in query.statuses) &&
                    (query.nameContains == null || trace.name.contains(query.nameContains, ignoreCase = true)) &&
                    (query.sinceWallMillis == null || trace.startWallMillis >= query.sinceWallMillis)
                if (matches) result += trace
            }
            result
        }
    }

    public suspend fun delete(id: TraceId): Boolean {
        val safeId = id.value.takeIf { SAFE_ID.matches(it) } ?: return false
        return guarded("delete", false) {
            mutex.withLock {
                storedTraceFiles().filter { it.name.endsWith("-$safeId.$EXTENSION") }.map { fileSystem.delete(it.name) }.any { it }
            }
        }
    }

    public suspend fun clearAll() {
        guarded("clear", Unit) {
            mutex.withLock {
                fileSystem.list().forEach { fileSystem.delete(it.name) }
            }
        }
    }

    public suspend fun storedBytes(): Long = guarded("stats", 0L) {
        mutex.withLock { storedTraceFiles().sumOf { it.sizeBytes } }
    }

    private fun recoverOnce() {
        if (recovered) return
        recovered = true
        fileSystem.list().filter { it.name.endsWith(TEMP_SUFFIX) }.forEach { fileSystem.delete(it.name) }
        enforceLimits()
    }

    private fun enforceLimits() {
        val now = clock.wallClockMillis()
        val cutoff = now - policy.retention.inWholeMilliseconds
        var files = storedTraceFiles().sortedBy { it.name }
        var evicted = 0
        files.filter { startOf(it.name) in 0 until cutoff }.forEach { if (fileSystem.delete(it.name)) evicted++ }
        files = storedTraceFiles().sortedBy { it.name }
        var count = files.size
        var bytes = files.sumOf { it.sizeBytes }
        for (file in files) {
            if (count <= policy.maxStoredTraces && bytes <= policy.maxTotalBytes) break
            if (fileSystem.delete(file.name)) {
                evicted++
                count--
                bytes -= file.sizeBytes
            }
        }
        if (evicted > 0) listener.onEvent(StorageEvent.Evicted(evicted))
    }

    private fun readTrace(name: String): JourneyTrace? {
        val text = fileSystem.read(name)
        val result = text?.let { importer.import(it) }
        val trace = (result as? ImportResult.Accepted)?.traces?.singleOrNull()
        if (trace == null) {
            fileSystem.delete(name)
            listener.onEvent(StorageEvent.CorruptFile(name))
        }
        return trace
    }

    private fun storedTraceFiles(): List<StoredFile> = fileSystem.list().filter { it.name.endsWith(".$EXTENSION") }

    private suspend fun <T> guarded(operation: String, fallback: T, block: suspend () -> T): T = try {
        withContext(dispatcher) { block() }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (t: Throwable) {
        listener.onEvent(StorageEvent.Failure(operation, t::class.simpleName ?: "Throwable"))
        if (operation == "save" && ++consecutiveFailures >= failureThreshold && !suspended) {
            suspended = true
            listener.onEvent(StorageEvent.CircuitOpened)
        }
        fallback
    }

    private fun fileName(startWall: Long, safeId: String): String =
        "${startWall.coerceAtLeast(0).toString().padStart(WALL_WIDTH, '0')}-$safeId.$EXTENSION"

    private fun startOf(name: String): Long = name.substringBefore('-').toLongOrNull() ?: -1

    public companion object {
        public const val DEFAULT_FAILURE_THRESHOLD: Int = 5
        internal const val TEMP_SUFFIX: String = ".tmp"
        private const val EXTENSION = TraceSchema.FILE_EXTENSION
        private const val WALL_WIDTH = 16
        private const val MAX_STORED_ITEMS = 10_000
        private val SAFE_ID = Regex("[A-Za-z0-9_.:-]{1,64}")
    }
}
