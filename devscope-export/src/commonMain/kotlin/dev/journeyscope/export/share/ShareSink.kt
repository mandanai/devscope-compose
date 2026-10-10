package dev.journeyscope.export.share

public class ExportedReport(
    public val fileName: String,
    public val json: String,
    public val traceCount: Int,
) {
    public val sizeBytes: Int get() = json.encodeToByteArray().size
}

/** Hands a report to the user's chosen destination; nothing is ever sent without this explicit call. */
public fun interface ShareSink {
    /** Returns false when the platform could not present a share UI. */
    public suspend fun share(report: ExportedReport): Boolean
}
