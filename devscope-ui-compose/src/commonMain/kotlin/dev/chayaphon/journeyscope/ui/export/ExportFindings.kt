package dev.chayaphon.journeyscope.ui.export

import dev.chayaphon.journeyscope.core.model.JourneyTrace
import dev.chayaphon.journeyscope.diagnostics.DiagnosticAnalyzer
import dev.chayaphon.journeyscope.export.schema.ExportedFinding

/** Adapter for `JourneyExportController(findings = analyzer.exportFindings())` so reports carry diagnostic findings. */
public fun DiagnosticAnalyzer.exportFindings(): (JourneyTrace) -> List<ExportedFinding> =
    { trace ->
        analyze(trace).findings.map { ExportedFinding(it.ruleId, it.severity.name, it.title, it.relatedSpanIds.firstOrNull()?.value) }
    }
