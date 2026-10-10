package dev.journeyscope.export.importing

import dev.journeyscope.core.model.JourneyTrace
import dev.journeyscope.export.schema.ExportedFinding

public data class ImportLimits(
    val maxBytes: Int = 2 * 1024 * 1024,
    val maxTraces: Int = 20,
    val maxSpansPerTrace: Int = 2_000,
    val maxEventsPerTrace: Int = 5_000,
    val maxMetadataEntries: Int = 64,
    val maxStringLength: Int = 512,
    val maxDepth: Int = 8,
    val maxIssues: Int = 20,
    val strict: Boolean = false,
) {
    init {
        require(maxBytes > 0 && maxTraces > 0 && maxSpansPerTrace > 0 && maxEventsPerTrace > 0) { "limits must be positive" }
        require(maxMetadataEntries >= 0 && maxStringLength > 0 && maxDepth > 0 && maxIssues > 0) { "limits must be positive" }
    }
}

public sealed class ImportResult {
    public data class Accepted(
        val traces: List<JourneyTrace>,
        val findings: Map<String, List<ExportedFinding>>,
        val warnings: List<String>,
    ) : ImportResult()

    public data class Rejected(val issues: List<String>) : ImportResult()
}
