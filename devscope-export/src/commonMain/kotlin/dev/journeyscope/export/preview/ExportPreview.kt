package dev.journeyscope.export.preview

import dev.journeyscope.export.redaction.RedactionReport

/** What an export would contain, computed from the redacted output so it matches the file byte for byte. */
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

/** Result of [JourneyExportController.export]. */
public sealed class ExportOutcome {
    public data class Ready(
        val report: dev.journeyscope.export.share.ExportedReport,
    ) : ExportOutcome()

    public data object NothingSelected : ExportOutcome()

    /** The selection exceeds the policy's trace-count or byte limit. */
    public data object TooLarge : ExportOutcome()
}
