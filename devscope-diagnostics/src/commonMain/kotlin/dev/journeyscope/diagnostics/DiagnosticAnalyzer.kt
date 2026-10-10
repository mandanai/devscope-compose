package dev.journeyscope.diagnostics

import dev.journeyscope.core.model.JourneyTrace

/**
 * Runs diagnostic rules over a finished or in-flight trace and returns evidence-based findings.
 * Pure and synchronous; a rule that throws is isolated and reported in [DiagnosticReport.ruleFailures] rather than failing the analysis.
 */
public class DiagnosticAnalyzer(
    public val config: DiagnosticConfig = DiagnosticConfig(),
    rules: List<DiagnosticRule> = DiagnosticRules.default(),
) {
    private val rules: List<DiagnosticRule> = rules.toList()

    /** Findings are ordered by severity (highest first), then earliest related span; [nowNanos] closes open spans for in-flight traces. */
    public fun analyze(
        trace: JourneyTrace,
        nowNanos: Long = trace.lastObservedNanos,
    ): DiagnosticReport {
        val timing = analyzeTiming(trace, nowNanos)
        val context = DiagnosticContext(trace, timing, config, nowNanos)
        val startOf = trace.spans.associate { it.id to it.startNanos }
        val failures = mutableListOf<RuleFailure>()
        val findings =
            rules
                .filter { it.id !in config.disabledRules }
                .flatMap { rule ->
                    try {
                        rule.evaluate(context).map { finding ->
                            config.severityOverrides[finding.ruleId]?.let { finding.copy(severity = it) } ?: finding
                        }
                    } catch (error: Exception) {
                        failures += RuleFailure(rule.id, error::class.simpleName ?: "Exception")
                        emptyList()
                    }
                }.sortedWith(
                    compareByDescending<DiagnosticFinding> { it.severity }
                        .thenBy { finding -> finding.relatedSpanIds.mapNotNull { startOf[it] }.minOrNull() ?: Long.MAX_VALUE }
                        .thenBy { it.ruleId },
                )
        return DiagnosticReport(findings, timing, failures)
    }
}
