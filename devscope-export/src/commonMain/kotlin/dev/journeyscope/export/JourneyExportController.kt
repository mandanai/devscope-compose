package dev.journeyscope.export

import dev.journeyscope.core.JourneyScope
import dev.journeyscope.core.model.JourneyTrace
import dev.journeyscope.core.recorder.JourneyRecorder
import dev.journeyscope.core.timing.JourneyClock
import dev.journeyscope.core.timing.SystemJourneyClock
import dev.journeyscope.export.importing.ImportLimits
import dev.journeyscope.export.importing.ImportResult
import dev.journeyscope.export.importing.TraceImporter
import dev.journeyscope.export.policy.ExportPolicy
import dev.journeyscope.export.preview.ExportOutcome
import dev.journeyscope.export.preview.ExportPreview
import dev.journeyscope.export.redaction.RedactedTrace
import dev.journeyscope.export.redaction.RedactionPipeline
import dev.journeyscope.export.redaction.RedactionReport
import dev.journeyscope.export.schema.ExportedFinding
import dev.journeyscope.export.schema.ReportContext
import dev.journeyscope.export.schema.TraceReportWriter
import dev.journeyscope.export.schema.TraceSchema
import dev.journeyscope.export.share.ExportedReport
import dev.journeyscope.export.share.ShareSink
import dev.journeyscope.export.storage.TraceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Coordinates review, export, share, import and history clearing. Nothing leaves the device unless the host
 * supplies a [ShareSink] and the user triggers [share].
 */
public class JourneyExportController(
    private val recorder: JourneyRecorder,
    private val store: TraceStore? = null,
    private val policy: ExportPolicy = ExportPolicy.Default,
    private val shareSink: ShareSink? = null,
    private val clock: JourneyClock = SystemJourneyClock,
    private val findings: (JourneyTrace) -> List<ExportedFinding> = { emptyList() },
    private val importLimits: ImportLimits = ImportLimits(),
) {
    private val pipeline = RedactionPipeline(policy)
    private val importer = TraceImporter(importLimits, pipeline)
    private val importedState = MutableStateFlow<List<JourneyTrace>>(emptyList())

    private val historyVersion = MutableStateFlow(0)

    /** Traces accepted by [importReport] this session; in memory only and capped by the import limits. */
    public val importedTraces: StateFlow<List<JourneyTrace>> get() = importedState.asStateFlow()

    /** Changes whenever persisted history is cleared, so observers know to reload [history]. */
    public val historyChanges: StateFlow<Int> get() = historyVersion.asStateFlow()

    /** False when no [ShareSink] was supplied. */
    public val canShare: Boolean get() = shareSink != null

    /** Persisted traces, or empty when no store is configured. */
    public suspend fun history(): List<JourneyTrace> = store?.loadAll().orEmpty()

    /** Renders the redacted report exactly as [export] would, without producing a file; null when nothing selected matches. */
    public suspend fun preview(ids: Set<String>): ExportPreview? {
        val selected = select(ids)
        if (selected.isEmpty()) return null
        val redacted = selected.map { pipeline.redact(it) }
        val json = render(redacted)
        val report = redacted.fold(RedactionReport()) { acc, item -> acc + item.report }
        return ExportPreview(
            traceCount = redacted.size,
            spanCount = redacted.sumOf { it.trace.spans.size },
            eventCount = redacted.sumOf { it.trace.events.size },
            sizeBytes = json.encodeToByteArray().size,
            includedMetadataKeys = report.includedKeys.sorted(),
            redaction = report,
            json = json,
            exceedsLimits = selected.size > policy.maxExportTraces || json.encodeToByteArray().size > policy.maxExportBytes,
        )
    }

    /** Redacts the selected traces and builds a report in memory; nothing is written or sent. */
    public suspend fun export(ids: Set<String>): ExportOutcome {
        val selected = select(ids)
        if (selected.isEmpty()) return ExportOutcome.NothingSelected
        if (selected.size > policy.maxExportTraces) return ExportOutcome.TooLarge
        val json = render(selected.map { pipeline.redact(it) })
        if (json.encodeToByteArray().size > policy.maxExportBytes) return ExportOutcome.TooLarge
        val name = "journeyscope-${clock.wallClockMillis()}.${TraceSchema.FILE_EXTENSION}"
        return ExportOutcome.Ready(ExportedReport(name, json, selected.size))
    }

    /** Hands the report to the [ShareSink]; returns false when none is configured or the platform could not present it. */
    public suspend fun share(report: ExportedReport): Boolean = shareSink?.share(report) ?: false

    /** Imported traces are re-redacted and deduplicated by id; malformed or oversized input is rejected, never thrown. */
    public fun importReport(text: String): ImportResult {
        val result = importer.import(text)
        if (result is ImportResult.Accepted) {
            importedState.update { current ->
                val known = current.mapTo(HashSet()) { it.id }
                (current + result.traces.filter { it.id !in known }).takeLast(importLimits.maxTraces)
            }
        }
        return result
    }

    /** Drops imported traces from memory only. */
    public fun clearImported() {
        importedState.value = emptyList()
    }

    /** Clears the recorder, the persisted store and imported traces. */
    public suspend fun clearHistory() {
        recorder.clear()
        store?.clearAll()
        clearImported()
        historyVersion.update { it + 1 }
    }

    private suspend fun select(ids: Set<String>): List<JourneyTrace> {
        if (ids.isEmpty()) return emptyList()
        val byId = LinkedHashMap<String, JourneyTrace>()
        history().forEach { byId[it.id.value] = it }
        recorder.snapshot().forEach { byId[it.id.value] = it }
        return ids.mapNotNull { byId[it] }.sortedBy { it.startWallMillis }
    }

    private fun render(traces: List<RedactedTrace>): String {
        val perTrace = traces.associate { it.trace.id.value to findings(it.trace) }
        return TraceReportWriter.write(
            traces,
            ReportContext(
                appVersion = policy.appVersion,
                exportedAtWallMillis = clock.wallClockMillis(),
                config = JourneyScope.tracer.config,
                findings = perTrace,
            ),
        )
    }
}
