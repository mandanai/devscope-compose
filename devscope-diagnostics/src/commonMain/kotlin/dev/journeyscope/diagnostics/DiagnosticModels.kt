package dev.journeyscope.diagnostics

import dev.journeyscope.core.model.JourneyTrace
import dev.journeyscope.core.model.SpanId
import kotlin.jvm.JvmInline

public enum class DiagnosticSeverity { Info, Warning, Error }

/** Open-ended grouping for findings; custom rules may create their own. */
@JvmInline
public value class DiagnosticCategory(
    public val name: String,
) {
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

/** Everything a [DiagnosticRule] may read; [timing] is precomputed for the same [nowNanos]. */
public class DiagnosticContext(
    public val trace: JourneyTrace,
    public val timing: TimingAnalysis,
    public val config: DiagnosticConfig,
    public val nowNanos: Long,
)

/** A single check. Must be side-effect free; implementations may throw, which isolates the failure to that rule. */
public interface DiagnosticRule {
    public val id: String

    public fun evaluate(context: DiagnosticContext): List<DiagnosticFinding>
}

/** Records that a rule threw, keeping only the exception type so no message or payload leaks into reports. */
public data class RuleFailure(
    val ruleId: String,
    val errorType: String,
)

/** Result of an analysis; [findings] are already sorted. */
public class DiagnosticReport(
    public val findings: List<DiagnosticFinding>,
    public val timing: TimingAnalysis,
    public val ruleFailures: List<RuleFailure> = emptyList(),
)
