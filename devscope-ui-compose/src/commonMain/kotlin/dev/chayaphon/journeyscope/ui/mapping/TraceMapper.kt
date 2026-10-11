package dev.chayaphon.journeyscope.ui.mapping

import dev.chayaphon.journeyscope.core.JourneyScopeConfig
import dev.chayaphon.journeyscope.core.expectation.ExpectedSequence
import dev.chayaphon.journeyscope.core.model.DiagnosticMetadata
import dev.chayaphon.journeyscope.core.model.JourneyEvent
import dev.chayaphon.journeyscope.core.model.JourneySpan
import dev.chayaphon.journeyscope.core.model.JourneyTrace
import dev.chayaphon.journeyscope.core.model.MetadataValue
import dev.chayaphon.journeyscope.core.model.SpanCategory
import dev.chayaphon.journeyscope.core.model.SpanStatus
import dev.chayaphon.journeyscope.core.model.TraceStatus
import dev.chayaphon.journeyscope.diagnostics.DiagnosticAnalyzer
import dev.chayaphon.journeyscope.diagnostics.DiagnosticFinding
import dev.chayaphon.journeyscope.diagnostics.Percentages
import dev.chayaphon.journeyscope.diagnostics.TimingAnalysis
import dev.chayaphon.journeyscope.diagnostics.analyzeTiming
import dev.chayaphon.journeyscope.ui.model.BreakdownOrder
import dev.chayaphon.journeyscope.ui.model.DiagnosticUi
import dev.chayaphon.journeyscope.ui.model.DurationBreakdownUi
import dev.chayaphon.journeyscope.ui.model.DurationSegmentUi
import dev.chayaphon.journeyscope.ui.model.JourneyCategory
import dev.chayaphon.journeyscope.ui.model.JourneyDetailUi
import dev.chayaphon.journeyscope.ui.model.JourneyListItemUi
import dev.chayaphon.journeyscope.ui.model.MetadataRowUi
import dev.chayaphon.journeyscope.ui.model.RelatedRowUi
import dev.chayaphon.journeyscope.ui.model.RowState
import dev.chayaphon.journeyscope.ui.model.SequenceCheckUi
import dev.chayaphon.journeyscope.ui.model.SequenceStepUi
import dev.chayaphon.journeyscope.ui.model.StatusKind
import dev.chayaphon.journeyscope.ui.model.StatusUi
import dev.chayaphon.journeyscope.ui.model.TimelineRowUi
import dev.chayaphon.journeyscope.ui.model.TimingBreakdownUi
import dev.chayaphon.journeyscope.ui.model.TimingRowUi
import dev.chayaphon.journeyscope.ui.model.displayName
import dev.chayaphon.journeyscope.ui.model.formatDuration
import dev.chayaphon.journeyscope.ui.model.spokenDuration
import dev.chayaphon.journeyscope.ui.model.toGlyph
import dev.chayaphon.journeyscope.ui.model.toJourneyCategory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

/** Durations above which the inspector flags a journey or span as slow. */
public data class InspectorThresholds(
    val slowJourney: Duration = 2_000.milliseconds,
    val slowSpan: Duration = 500.milliseconds,
) {
    public companion object {
        public fun from(config: JourneyScopeConfig): InspectorThresholds = InspectorThresholds(slowJourney = config.slowJourneyThreshold)
    }
}

private const val MAX_INDENT_DEPTH = 3
private const val HIDDEN_VALUE = "Hidden by display policy"

private val NETWORK_FAILURE_LABELS =
    mapOf(
        "TIMEOUT" to "Timeout",
        "CONNECTION" to "Connection failed",
        "DNS" to "DNS lookup failed",
        "TLS" to "TLS error",
        "CANCELLED" to "Cancelled",
        "HTTP_CLIENT_ERROR" to "HTTP client error",
        "HTTP_SERVER_ERROR" to "HTTP server error",
        "PROTOCOL" to "Protocol error",
        "UNKNOWN" to "Network error",
    )

internal class TraceMapper(
    private val thresholds: InspectorThresholds,
    private val policy: MetadataDisplayPolicy,
    private val analyzer: DiagnosticAnalyzer,
    private val expectedSequences: List<ExpectedSequence> = emptyList(),
) {
    fun listItem(
        trace: JourneyTrace,
        nowNanos: Long,
    ): JourneyListItemUi {
        val timing = analyzeTiming(trace, nowNanos)
        val breakdown = breakdown(timing)
        val status = status(trace, timing.total)
        return JourneyListItemUi(
            id = trace.id.value,
            title = trace.name,
            subtitle = subtitle(trace),
            status = status,
            breakdown = breakdown,
            spokenDescription = "${trace.name}, ${status.label}, ${breakdown.spokenDescription}",
        )
    }

    fun detail(
        trace: JourneyTrace,
        nowNanos: Long,
    ): JourneyDetailUi {
        val report = analyzer.analyze(trace, nowNanos)
        val timing = report.timing
        val breakdown = breakdown(timing)
        val rows = rows(trace, timing.spans.associateBy { it.spanId.value }, nowNanos)
        return JourneyDetailUi(
            id = trace.id.value,
            title = "Journey: ${trace.name}",
            subtitle = subtitle(trace),
            status = status(trace, timing.total),
            breakdown = breakdown,
            rows = rows,
            diagnostics = report.findings.map { diagnostic(it, rows) },
            timing = timingBreakdown(trace, timing, breakdown),
            failureLabel = trace.failure?.let { failureText(it.type, it.message) },
            droppedItems = trace.droppedItems,
            sequences =
                expectedSequences.filter { it.appliesTo(trace) }.map { sequence ->
                    val check = sequence.evaluate(trace)
                    SequenceCheckUi(check.name, check.steps.map { SequenceStepUi(it.label, it.observed) })
                },
        )
    }

    private fun subtitle(trace: JourneyTrace): String {
        val source =
            (trace.metadata[MetadataDisplayPolicy.SOURCE_KEY] as? MetadataValue.Text)
                ?.value
                ?.takeIf { policy.isVisible(MetadataDisplayPolicy.SOURCE_KEY) }
        return buildString {
            append("Trace #").append(trace.id.value)
            if (source != null) append(" · ").append(source)
        }
    }

    private fun status(
        trace: JourneyTrace,
        duration: Duration,
    ): StatusUi =
        when (trace.status) {
            TraceStatus.CREATED -> {
                StatusUi(StatusKind.Created, "Not started")
            }

            TraceStatus.RUNNING -> {
                StatusUi(StatusKind.Running, "Running")
            }

            TraceStatus.FAILED -> {
                StatusUi(StatusKind.Failed, "Failed")
            }

            TraceStatus.CANCELLED -> {
                StatusUi(StatusKind.Cancelled, "Cancelled")
            }

            TraceStatus.INTERRUPTED -> {
                StatusUi(StatusKind.Interrupted, "Interrupted")
            }

            TraceStatus.COMPLETED -> {
                if (duration >= thresholds.slowJourney) {
                    StatusUi(StatusKind.Slow, "Slow Flow")
                } else {
                    StatusUi(StatusKind.Completed, "Completed")
                }
            }
        }

    private fun attributedByCategory(timing: TimingAnalysis): Map<JourneyCategory, Long> {
        val result = HashMap<JourneyCategory, Long>()
        timing.categories.forEach { entry ->
            val category = entry.category.toJourneyCategory()
            result[category] = (result[category] ?: 0L) + entry.attributedNanos
        }
        if (timing.untrackedNanos > 0L) result[JourneyCategory.Idle] = timing.untrackedNanos
        return result
    }

    private fun breakdown(timing: TimingAnalysis): DurationBreakdownUi {
        val totalNanos = timing.totalNanos
        val byCategory = attributedByCategory(timing)
        val entries = BreakdownOrder.mapNotNull { category -> byCategory[category]?.takeIf { it > 0L }?.let { category to it } }
        val percents = Percentages.distribute(entries.map { it.second }, totalNanos)
        val segments =
            entries.mapIndexed { index, (category, nanos) ->
                DurationSegmentUi(
                    category = category,
                    nanos = nanos,
                    label = formatDuration(nanos),
                    percent = percents[index],
                    weight = (nanos.toDouble() / totalNanos).toFloat(),
                )
            }
        val spoken =
            buildString {
                append("total ").append(spokenDuration(totalNanos))
                segments.forEach {
                    append(", ")
                        .append(it.category.label)
                        .append(' ')
                        .append(it.percent)
                        .append(" percent")
                }
            }
        return DurationBreakdownUi(totalNanos, formatDuration(totalNanos), segments, spoken)
    }

    private fun timingBreakdown(
        trace: JourneyTrace,
        timing: TimingAnalysis,
        breakdown: DurationBreakdownUi,
    ): TimingBreakdownUi {
        val byCategory = attributedByCategory(timing)
        val rows =
            BreakdownOrder.mapNotNull { category ->
                byCategory[category]?.takeIf { it > 0L }?.let {
                    TimingRowUi(category, formatDuration(it), Percentages.format(it, timing.totalNanos))
                }
            }
        val names = trace.spans.associate { it.id to it.name }
        val slowest =
            timing.slowestSpan?.let { id ->
                val exclusive =
                    timing.spans
                        .firstOrNull { it.spanId == id }
                        ?.exclusive
                        ?.inWholeNanoseconds ?: 0L
                "${names[id] ?: id.value} · ${formatDuration(exclusive)} own time"
            }
        val critical =
            timing.criticalPath
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString(" → ") { names[it] ?: it.value }
                ?.let { "Critical path: $it" }
                ?: if (timing.criticalPath == null) {
                    "Critical path unavailable: overlapping spans do not say which one the others waited on."
                } else {
                    "Critical path: none recorded."
                }
        val overlap =
            if (timing.isSequential) {
                null
            } else {
                "${timing.overlaps.size} overlapping span pair(s), up to ${timing.maxConcurrency} running at once. " +
                    "Each moment is counted once, so percentages cover wall-clock time, not summed span durations."
            }
        return TimingBreakdownUi(breakdown.totalLabel, rows, overlap, slowest, critical)
    }

    private fun diagnostic(
        finding: DiagnosticFinding,
        rows: List<TimelineRowUi>,
    ): DiagnosticUi {
        val byId = rows.associateBy { it.id }
        return DiagnosticUi(
            ruleId = finding.ruleId,
            severity = finding.severity,
            categoryLabel = finding.category.name,
            title = finding.title,
            evidence = finding.evidence,
            inference = finding.inference,
            nextSteps = finding.nextSteps,
            related = finding.relatedSpanIds.mapNotNull { id -> byId[id.value]?.let { RelatedRowUi(it.id, it.title) } },
            metadata = finding.metrics.map { (key, value) -> MetadataRowUi(key, value, hidden = false) },
        )
    }

    private fun rows(
        trace: JourneyTrace,
        timings: Map<String, dev.chayaphon.journeyscope.core.timing.SpanTiming>,
        nowNanos: Long,
    ): List<TimelineRowUi> {
        val depthOf = depths(trace.spans)
        val spanRows =
            trace.spans.map { span ->
                val timing = timings[span.id.value]
                val inclusive =
                    timing?.inclusive?.inWholeNanoseconds
                        ?: ((span.endNanos ?: nowNanos) - span.startNanos).coerceAtLeast(0L)
                val self = timing?.exclusive?.inWholeNanoseconds ?: inclusive
                SortableRow(span.startNanos, span.sequence, spanRow(trace, span, inclusive, self, depthOf[span.id.value] ?: 0))
            }
        val eventRows =
            trace.events.map { event ->
                val offset = (event.atNanos - trace.startNanos).coerceAtLeast(0L)
                val category = event.category.toJourneyCategory()
                SortableRow(
                    event.atNanos,
                    event.sequence,
                    TimelineRowUi(
                        id = "event:${event.id.value}",
                        title = event.name,
                        detail = detailText(event.metadata) ?: event.category.displayName(),
                        category = category,
                        glyph = event.category.toGlyph(),
                        durationLabel = "+" + formatDuration(offset),
                        selfNanos = 0L,
                        offsetLabel = "At +" + formatDuration(offset),
                        state = RowState.Event,
                        isBottleneck = false,
                        depth = 0,
                        selfLabel = null,
                        failureLabel = null,
                        metadata = metadataRows(event.metadata) + checkpointRows(trace, event),
                        spokenDescription = "${event.name}, event, ${spokenDuration(offset)} after start",
                    ),
                )
            }
        return (spanRows + eventRows).sortedWith(compareBy({ it.nanos }, { it.sequence })).map { it.row }
    }

    private fun spanRow(
        trace: JourneyTrace,
        span: JourneySpan,
        inclusive: Long,
        self: Long,
        depth: Int,
    ): TimelineRowUi {
        val state =
            when (span.status) {
                SpanStatus.RUNNING -> RowState.Running
                SpanStatus.OK -> RowState.Ok
                SpanStatus.FAILED -> RowState.Failed
                SpanStatus.CANCELLED -> RowState.Cancelled
                SpanStatus.INTERRUPTED -> RowState.Interrupted
            }
        val bottleneck = self >= thresholds.slowSpan.inWholeNanoseconds
        val detail = detailText(span.metadata) ?: span.category.displayName()
        val failure = networkFailureLabel(span) ?: span.failure?.let { failureText(it.type, it.message) }
        val offset = (span.startNanos - trace.startNanos).coerceAtLeast(0L)
        val spoken =
            buildString {
                append(span.name)
                    .append(", ")
                    .append(detail)
                    .append(", took ")
                    .append(spokenDuration(inclusive))
                when (state) {
                    RowState.Running -> append(", still running")
                    RowState.Failed -> append(", failed")
                    RowState.Cancelled -> append(", cancelled")
                    RowState.Interrupted -> append(", interrupted")
                    else -> Unit
                }
                if (bottleneck) append(", slow step")
            }
        return TimelineRowUi(
            id = span.id.value,
            title = span.name,
            detail = detail,
            category = span.category.toJourneyCategory(),
            glyph = span.category.toGlyph(),
            durationLabel = formatDuration(inclusive),
            selfNanos = self,
            offsetLabel = "Started +" + formatDuration(offset),
            state = state,
            isBottleneck = bottleneck,
            depth = depth.coerceAtMost(MAX_INDENT_DEPTH),
            selfLabel = formatDuration(self).takeIf { self != inclusive },
            failureLabel = failure,
            metadata = metadataRows(span.metadata),
            spokenDescription = spoken,
        )
    }

    private fun checkpointRows(
        trace: JourneyTrace,
        event: JourneyEvent,
    ): List<MetadataRowUi> {
        if (event.category != SpanCategory.STATE || !policy.isVisible(MetadataDisplayPolicy.UI_CHECKPOINT_KEY)) return emptyList()
        val observed = (event.metadata["state.event"] as? MetadataValue.Text)?.value == "observed"
        if (!observed) return emptyList()
        val checkpoint =
            trace.spans
                .filter { it.category == SpanCategory.UI_RENDER && it.startNanos >= event.atNanos }
                .minByOrNull { it.startNanos } ?: return emptyList()
        return listOf(MetadataRowUi(MetadataDisplayPolicy.UI_CHECKPOINT_KEY, checkpoint.name, hidden = false))
    }

    private fun networkFailureLabel(span: JourneySpan): String? {
        if (span.category != SpanCategory.NETWORK || span.status != SpanStatus.FAILED) return null
        val kind = (span.metadata["http.failure"] as? MetadataValue.Text)?.value ?: return null
        val status = (span.metadata["http.status"] as? MetadataValue.Integer)?.value
        return NETWORK_FAILURE_LABELS[kind]?.let { label -> if (status != null) "$label · HTTP $status" else label }
    }

    private fun failureText(
        type: String,
        message: String?,
    ): String = if (policy.showFailureMessages && !message.isNullOrBlank()) "$type: $message" else type

    private fun detailText(metadata: DiagnosticMetadata): String? =
        (metadata[MetadataDisplayPolicy.DETAIL_KEY] as? MetadataValue.Text)
            ?.value
            ?.takeIf { policy.isVisible(MetadataDisplayPolicy.DETAIL_KEY) && it.isNotBlank() }

    private fun metadataRows(metadata: DiagnosticMetadata): List<MetadataRowUi> =
        metadata.entries
            .filterKeys { it != MetadataDisplayPolicy.DETAIL_KEY }
            .entries
            .sortedBy { it.key }
            .map { (key, value) ->
                if (policy.isVisible(key)) {
                    MetadataRowUi(key, value.display(), hidden = false)
                } else {
                    MetadataRowUi(key, HIDDEN_VALUE, hidden = true)
                }
            }

    private fun depths(spans: List<JourneySpan>): Map<String, Int> {
        val parents = spans.associate { it.id.value to it.parentId?.value }
        return spans.associate { span ->
            var depth = 0
            var cursor = parents[span.id.value]
            while (cursor != null && cursor in parents && depth < MAX_INDENT_DEPTH + 1) {
                depth++
                cursor = parents[cursor]
            }
            span.id.value to depth
        }
    }

    private class SortableRow(
        val nanos: Long,
        val sequence: Long,
        val row: TimelineRowUi,
    )
}

private fun MetadataValue.display(): String =
    when (this) {
        is MetadataValue.Text -> value
        is MetadataValue.Integer -> value.toString()
        is MetadataValue.Decimal -> value.toString()
        is MetadataValue.Flag -> value.toString()
    }
