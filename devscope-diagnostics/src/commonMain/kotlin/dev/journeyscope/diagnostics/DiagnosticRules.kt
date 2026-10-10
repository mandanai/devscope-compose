package dev.journeyscope.diagnostics

import dev.journeyscope.core.model.JourneySpan
import dev.journeyscope.core.model.MetadataValue
import dev.journeyscope.core.model.SpanCategory
import dev.journeyscope.core.model.SpanStatus
import dev.journeyscope.core.model.TraceMetadataKeys
import dev.journeyscope.core.model.TraceStatus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

public object DiagnosticRules {
    /** Returns the built-in rules; pass a modified list to [DiagnosticAnalyzer] to add or remove checks. */
    public fun default(): List<DiagnosticRule> =
        listOf(
            SlowJourneyRule,
            SlowNetworkSpanRule,
            SlowDatabaseOperationRule,
            FailedNetworkOperationRule,
            BusinessValidationFailureRule,
            RepeatedNetworkRequestsRule,
            SequentialDatabaseOperationsRule,
            LongOperationGapRule,
            UnfinishedJourneyRule,
            MissingExpectedCheckpointRule,
            DominantContributorRule,
            DatabaseSignificantRule,
        )
}

internal fun JourneySpan.text(key: String): String? = (metadata[key] as? MetadataValue.Text)?.value

internal fun JourneySpan.durationOrNull(): Duration? = endNanos?.let { (it - startNanos).coerceAtLeast(0).nanoseconds }

internal fun Duration.label(): String = "${(inWholeNanoseconds + HALF_MILLI_NANOS) / NANOS_PER_MILLI} ms"

private const val NANOS_PER_MILLI = 1_000_000L
private const val HALF_MILLI_NANOS = 500_000L

private fun DiagnosticContext.spansOf(category: SpanCategory) = trace.spans.filter { it.category == category }

private fun DiagnosticContext.limited(spans: List<JourneySpan>) = spans.take(config.maxRelatedSpans)

private val JourneySpan.isFailure: Boolean get() = status == SpanStatus.FAILED

internal fun SpanCategory.humanLabel(): String = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun slowSpans(
    spans: List<JourneySpan>,
    threshold: Duration,
): List<Pair<JourneySpan, Duration>> =
    spans
        .filter { !it.isFailure && it.status != SpanStatus.CANCELLED }
        .mapNotNull { span -> span.durationOrNull()?.takeIf { it >= threshold }?.let { span to it } }
        .sortedByDescending { it.second }

internal object SlowJourneyRule : DiagnosticRule {
    override val id = "journey.slow"

    override fun evaluate(context: DiagnosticContext): List<DiagnosticFinding> {
        val total = context.timing.total
        if (context.trace.status != TraceStatus.COMPLETED || total < context.config.slowJourney) return emptyList()
        return listOf(
            DiagnosticFinding(
                ruleId = id,
                severity = DiagnosticSeverity.Warning,
                category = DiagnosticCategory.Performance,
                title = "Slow journey",
                evidence = listOf("The journey took ${total.label()} (threshold ${context.config.slowJourney.label()})."),
                nextSteps = listOf("Open the timing breakdown to see which category dominates."),
                metrics = mapOf("duration" to total.label()),
            ),
        )
    }
}

internal object SlowNetworkSpanRule : DiagnosticRule {
    override val id = "network.slow"

    override fun evaluate(context: DiagnosticContext): List<DiagnosticFinding> {
        val slow = slowSpans(context.spansOf(SpanCategory.NETWORK), context.config.slowNetworkSpan)
        if (slow.isEmpty()) return emptyList()
        return listOf(
            DiagnosticFinding(
                ruleId = id,
                severity = DiagnosticSeverity.Warning,
                category = DiagnosticCategory.Network,
                title = "Slow network operation",
                evidence =
                    slow.take(context.config.maxRelatedSpans).map { (span, duration) ->
                        "${span.name} took ${duration.label()} (threshold ${context.config.slowNetworkSpan.label()})."
                    },
                inference = "Time was spent between sending the request and finishing the response; client, network and backend latency cannot be told apart from this trace.",
                nextSteps = listOf("Compare with backend-side timing for the same request.", "Check connection reuse, payload size and retries."),
                relatedSpanIds = slow.take(context.config.maxRelatedSpans).map { it.first.id },
                metrics = mapOf("slowest" to slow.first().second.label()),
            ),
        )
    }
}

internal object SlowDatabaseOperationRule : DiagnosticRule {
    override val id = "database.slow"

    override fun evaluate(context: DiagnosticContext): List<DiagnosticFinding> {
        val database = context.spansOf(SpanCategory.DATABASE)
        val parents = database.mapNotNull { it.parentId }.toSet()
        val slow = slowSpans(database.filter { it.id !in parents }, context.config.slowDatabaseOperation)
        if (slow.isEmpty()) return emptyList()
        return listOf(
            DiagnosticFinding(
                ruleId = id,
                severity = DiagnosticSeverity.Warning,
                category = DiagnosticCategory.Database,
                title = "Slow database operation",
                evidence =
                    slow.take(context.config.maxRelatedSpans).map { (span, duration) ->
                        "${span.text(TraceMetadataKeys.DB_LABEL) ?: span.name} took ${duration.label()} (threshold ${context.config.slowDatabaseOperation.label()})."
                    },
                nextSteps = listOf("Review the query, indexes and data volume.", "Confirm the operation is not running on the main thread."),
                relatedSpanIds = slow.take(context.config.maxRelatedSpans).map { it.first.id },
                metrics = mapOf("slowest" to slow.first().second.label()),
            ),
        )
    }
}

internal object FailedNetworkOperationRule : DiagnosticRule {
    override val id = "network.failed"

    override fun evaluate(context: DiagnosticContext): List<DiagnosticFinding> {
        val failed = context.spansOf(SpanCategory.NETWORK).filter { it.isFailure }
        if (failed.isEmpty()) return emptyList()
        return listOf(
            DiagnosticFinding(
                ruleId = id,
                severity = DiagnosticSeverity.Error,
                category = DiagnosticCategory.Network,
                title = "Network operation failed",
                evidence =
                    context.limited(failed).map { span ->
                        val reason =
                            span.text(TraceMetadataKeys.HTTP_FAILURE)
                                ?: (span.metadata[TraceMetadataKeys.HTTP_STATUS] as? MetadataValue.Integer)?.let { "HTTP ${it.value}" }
                                ?: span.failure?.type
                                ?: "unknown"
                        "${span.name} failed ($reason)."
                    },
                nextSteps = listOf("Check connectivity, server health and the request that was sent."),
                relatedSpanIds = context.limited(failed).map { it.id },
                metrics = mapOf("failed" to failed.size.toString()),
            ),
        )
    }
}

internal object BusinessValidationFailureRule : DiagnosticRule {
    override val id = "business.validation_failed"

    override fun evaluate(context: DiagnosticContext): List<DiagnosticFinding> {
        val validation = context.spansOf(SpanCategory.BUSINESS_VALIDATION).filter { it.isFailure }
        val response =
            context.spansOf(SpanCategory.NETWORK).filter {
                it.status == SpanStatus.OK && it.text(TraceMetadataKeys.BUSINESS_STATUS).equals("FAILURE", ignoreCase = true)
            }
        if (validation.isEmpty() && response.isEmpty()) return emptyList()
        val evidence =
            buildList {
                response.forEach { span ->
                    val status = (span.metadata[TraceMetadataKeys.HTTP_STATUS] as? MetadataValue.Integer)?.value
                    add("${span.name} reported a business failure${status?.let { " despite HTTP $it" } ?: ""}.")
                }
                validation.forEach { add("${it.name} failed.") }
            }
        return listOf(
            DiagnosticFinding(
                ruleId = id,
                severity = DiagnosticSeverity.Error,
                category = DiagnosticCategory.Business,
                title = if (response.isNotEmpty()) "Business validation failed despite a successful response" else "Business validation failed",
                evidence = evidence.take(context.config.maxRelatedSpans),
                inference = if (response.isNotEmpty()) "The transport succeeded; the rejection came from the response payload." else null,
                nextSteps = listOf("Inspect the business return code and the rule that rejected the request."),
                relatedSpanIds = (response + validation).take(context.config.maxRelatedSpans).map { it.id },
            ),
        )
    }
}

internal object RepeatedNetworkRequestsRule : DiagnosticRule {
    override val id = "network.repeated"

    override fun evaluate(context: DiagnosticContext): List<DiagnosticFinding> {
        val groups =
            context
                .spansOf(SpanCategory.NETWORK)
                .groupBy { it.text(TraceMetadataKeys.HTTP_METHOD).orEmpty() + " " + it.text(TraceMetadataKeys.HTTP_HOST).orEmpty() + it.text(TraceMetadataKeys.HTTP_PATH).orEmpty() }
                .filter { it.value.size >= context.config.repeatedRequestMinCount }
        if (groups.isEmpty()) return emptyList()
        val related = groups.values.flatten()
        val retries = groups.values.any { group -> group.dropLast(1).any { it.isFailure } }
        return listOf(
            DiagnosticFinding(
                ruleId = id,
                severity = DiagnosticSeverity.Warning,
                category = DiagnosticCategory.Network,
                title = "Multiple similar requests in one journey",
                evidence = groups.map { (key, spans) -> "${spans.size} requests matched ${key.trim()} during this journey." },
                inference = if (retries) "Earlier attempts failed, so these may be retries." else "The same endpoint was called repeatedly; this may be redundant work.",
                nextSteps = listOf("Check for duplicate triggers, missing caching or unintended retry loops."),
                relatedSpanIds = context.limited(related).map { it.id },
                metrics = mapOf("requests" to related.size.toString()),
            ),
        )
    }
}

internal object SequentialDatabaseOperationsRule : DiagnosticRule {
    override val id = "database.sequential_burst"

    override fun evaluate(context: DiagnosticContext): List<DiagnosticFinding> {
        val database = context.spansOf(SpanCategory.DATABASE)
        val parents = database.mapNotNull { it.parentId }.toSet()
        val bursts =
            database
                .filter { it.id !in parents }
                .groupBy { it.parentId }
                .mapValues { (_, spans) -> spans.sortedBy { it.startNanos } }
                .filter { (_, spans) -> spans.size >= context.config.sequentialDatabaseMinCount && spans.zipWithNext().all { (a, b) -> (a.endNanos ?: Long.MAX_VALUE) <= b.startNanos } }
        if (bursts.isEmpty()) return emptyList()
        val related = bursts.values.flatten()
        return listOf(
            DiagnosticFinding(
                ruleId = id,
                severity = DiagnosticSeverity.Warning,
                category = DiagnosticCategory.Database,
                title = "Many sequential database operations",
                evidence = bursts.values.map { "${it.size} database operations ran one after another under the same parent." },
                inference = "Batching them into one transaction or query might reduce overhead.",
                nextSteps = listOf("Look for per-item queries inside a loop."),
                relatedSpanIds = context.limited(related).map { it.id },
            ),
        )
    }
}

internal object LongOperationGapRule : DiagnosticRule {
    override val id = "journey.gap"

    override fun evaluate(context: DiagnosticContext): List<DiagnosticFinding> {
        val threshold = maxOf(context.config.minGap, (context.timing.totalNanos * context.config.gapFractionOfJourney).toLong().nanoseconds)
        val gaps = mutableListOf<Triple<JourneySpan, JourneySpan, Duration>>()
        context.trace.spans.groupBy { it.parentId }.values.forEach { siblings ->
            val ordered = siblings.filter { !it.isOpen }.sortedWith(compareBy({ it.startNanos }, { it.sequence }))
            var previous: JourneySpan? = null
            for (span in ordered) {
                val before = previous
                if (before != null) {
                    val gap = (span.startNanos - (before.endNanos ?: span.startNanos)).nanoseconds
                    if (gap >= threshold) gaps += Triple(before, span, gap)
                }
                if (before == null || (span.endNanos ?: 0) > (before.endNanos ?: 0)) previous = span
            }
        }
        if (gaps.isEmpty()) return emptyList()
        val top = gaps.sortedByDescending { it.third }.take(context.config.maxRelatedSpans)
        return listOf(
            DiagnosticFinding(
                ruleId = id,
                severity = DiagnosticSeverity.Info,
                category = DiagnosticCategory.Instrumentation,
                title = "Unexplained gap between operations",
                evidence = top.map { (a, b, gap) -> "${gap.label()} elapsed between the end of ${a.name} and the start of ${b.name}." },
                inference = "Work between these boundaries is not instrumented, or the system was waiting (scheduling, dispatch, user delay).",
                nextSteps = listOf("Add spans around the untracked work, or check dispatcher queueing."),
                relatedSpanIds = top.flatMap { listOf(it.first.id, it.second.id) }.distinct(),
            ),
        )
    }
}

internal object UnfinishedJourneyRule : DiagnosticRule {
    override val id = "journey.unfinished"

    override fun evaluate(context: DiagnosticContext): List<DiagnosticFinding> {
        val closedBySystem = context.trace.spans.filter { it.endedBySystem }
        if (context.trace.status != TraceStatus.INTERRUPTED && closedBySystem.isEmpty()) return emptyList()
        return listOf(
            DiagnosticFinding(
                ruleId = id,
                severity = DiagnosticSeverity.Warning,
                category = DiagnosticCategory.Reliability,
                title = "Journey was interrupted before completing",
                evidence =
                    buildList {
                        if (context.trace.status == TraceStatus.INTERRUPTED) add("The journey ended with status INTERRUPTED.")
                        context.limited(closedBySystem).forEach { add("${it.name} was still open and was closed by the SDK.") }
                    },
                inference = "The process, screen or scope that owned the work may have ended first.",
                nextSteps = listOf("Check lifecycle events and scope cancellation around the last recorded step."),
                relatedSpanIds = context.limited(closedBySystem).map { it.id },
            ),
        )
    }
}

internal object MissingExpectedCheckpointRule : DiagnosticRule {
    override val id = "journey.checkpoint_missing"

    override fun evaluate(context: DiagnosticContext): List<DiagnosticFinding> {
        if (context.trace.status != TraceStatus.COMPLETED) return emptyList()
        val emissionObserved = context.trace.events.any { it.category == SpanCategory.STATE_EMISSION }
        return context.config.expectedSequences.filter { it.appliesTo(context.trace) }.mapNotNull { sequence ->
            val missing = sequence.evaluate(context.trace).missing
            if (missing.isEmpty()) return@mapNotNull null
            DiagnosticFinding(
                ruleId = id,
                severity = DiagnosticSeverity.Warning,
                category = DiagnosticCategory.Instrumentation,
                title = "Expected checkpoint not recorded",
                evidence =
                    buildList {
                        add("${sequence.name}: not observed — ${missing.joinToString()}.")
                        if (emissionObserved) add("UI state emission was observed, but the configured completion checkpoint was not recorded.")
                    },
                inference = "Either the step did not happen or it is not instrumented; this trace cannot tell which.",
                nextSteps = listOf("Verify the checkpoint call exists on this code path and runs after state emission."),
            )
        }
    }
}

private fun dominantText(
    category: SpanCategory,
    percent: String,
    sequential: Boolean,
): String {
    val scope = if (sequential) "of this sequential journey" else "of the attributed wall-clock time (overlapping work is present)"
    return "${category.humanLabel()} time represents approximately $percent $scope."
}

internal object DominantContributorRule : DiagnosticRule {
    override val id = "timing.dominant"

    override fun evaluate(context: DiagnosticContext): List<DiagnosticFinding> {
        val timing = context.timing
        if (!timing.isComplete || timing.totalNanos <= 0) return emptyList()
        val top = timing.categories.firstOrNull() ?: return emptyList()
        if (top.attributedNanos.toDouble() / timing.totalNanos < context.config.dominantShare) return emptyList()
        val percent = Percentages.format(top.attributedNanos, timing.totalNanos)
        val next =
            when (top.category) {
                SpanCategory.NETWORK -> "Investigate network latency or backend response time."
                SpanCategory.DATABASE -> "Review query cost and data volume."
                else -> "Inspect the longest ${top.category.humanLabel().lowercase()} steps in the timeline."
            }
        return listOf(
            DiagnosticFinding(
                ruleId = id,
                severity = DiagnosticSeverity.Info,
                category = DiagnosticCategory.Performance,
                title = "${top.category.humanLabel()} dominates the journey",
                evidence = listOf(dominantText(top.category, percent, timing.isSequential), "Attributed ${top.attributed.label()} of ${timing.total.label()}."),
                inference = "Share of time alone does not prove a root cause.",
                nextSteps = listOf(next),
                relatedSpanIds =
                    context.trace.spans
                        .filter { it.category == top.category }
                        .sortedByDescending { it.durationOrNull() ?: Duration.ZERO }
                        .take(context.config.maxRelatedSpans)
                        .map { it.id },
                metrics = mapOf("share" to percent),
            ),
        )
    }
}

internal object DatabaseSignificantRule : DiagnosticRule {
    override val id = "timing.database_significant"

    override fun evaluate(context: DiagnosticContext): List<DiagnosticFinding> {
        val timing = context.timing
        if (!timing.isComplete || timing.totalNanos <= 0) return emptyList()
        if (timing.categories.firstOrNull()?.category == SpanCategory.DATABASE) return emptyList()
        val database = timing.categories.firstOrNull { it.category == SpanCategory.DATABASE } ?: return emptyList()
        if (database.attributedNanos.toDouble() / timing.totalNanos < context.config.databaseSignificantShare) return emptyList()
        val percent = Percentages.format(database.attributedNanos, timing.totalNanos)
        return listOf(
            DiagnosticFinding(
                ruleId = id,
                severity = DiagnosticSeverity.Info,
                category = DiagnosticCategory.Database,
                title = "Database operations are a significant contributor",
                evidence = listOf("Database operations account for approximately $percent of the measured journey duration."),
                nextSteps = listOf("Review query cost and whether work can move off the critical path."),
                relatedSpanIds =
                    context.trace.spans
                        .filter { it.category == SpanCategory.DATABASE }
                        .take(context.config.maxRelatedSpans)
                        .map { it.id },
                metrics = mapOf("share" to percent),
            ),
        )
    }
}
