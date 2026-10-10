package dev.journeyscope.export.preview

import dev.journeyscope.export.redaction.RedactionReport

public data class ExportPreview(
    val traceCount: Int,
    val spanCount: Int,
    val eventCount: Int,
    val sizeBytes: Int,
    val includedMetadataKeys: List<String>,
    val redaction: RedactionReport,
    val json: String,
    val exceedsLimits: Boolean,
)

public sealed class ExportOutcome {
    public data class Ready(val report: dev.journeyscope.export.share.ExportedReport) : ExportOutcome()
    public data object NothingSelected : ExportOutcome()
    public data object TooLarge : ExportOutcome()
}
