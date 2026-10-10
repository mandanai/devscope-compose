package dev.journeyscope.diagnostics

import dev.journeyscope.core.model.JourneyTrace
import dev.journeyscope.core.model.SpanId
import kotlin.jvm.JvmInline

public enum class DiagnosticSeverity { Info, Warning, Error }

@JvmInline
public value class DiagnosticCategory(public val name: String) {
    public companion object {
        public val Performance: DiagnosticCategory = DiagnosticCategory("Performance")
        public val Network: DiagnosticCategory = DiagnosticCategory("Network")
        public val Database: DiagnosticCategory = DiagnosticCategory("Database")
        public val Business: DiagnosticCategory = DiagnosticCategory("Business")
        public val Reliability: DiagnosticCategory = DiagnosticCategory("Reliability")
        public val Instrumentation: DiagnosticCategory = DiagnosticCategory("Instrumentation")
    }
}

/**
 * [evidence] states what was observed, [inference] what it might mean (null when nothing
 * defensible can be said), [nextSteps] what to investigate.
 */
public data class DiagnosticFinding(
    val ruleId: String,
    val severity: DiagnosticSeverity,
    val category: DiagnosticCategory,
    val title: String,
    val evidence: List<String>,
    val inference: String? = null,
    val nextSteps: List<String> = emptyList(),
    val relatedSpanIds: List<SpanId> = emptyList(),
    val metrics: Map<String, String> = emptyMap(),
)

public class DiagnosticContext(
    public val trace: JourneyTrace,
    public val timing: TimingAnalysis,
    public val config: DiagnosticConfig,
    public val nowNanos: Long,
)

public interface DiagnosticRule {
    public val id: String
    public fun evaluate(context: DiagnosticContext): List<DiagnosticFinding>
}

public data class RuleFailure(val ruleId: String, val errorType: String)

public class DiagnosticReport(
    public val findings: List<DiagnosticFinding>,
    public val timing: TimingAnalysis,
    public val ruleFailures: List<RuleFailure> = emptyList(),
)
