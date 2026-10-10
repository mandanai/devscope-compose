package dev.journeyscope.ui.mapping

import dev.journeyscope.core.JourneyScopeConfig
import dev.journeyscope.core.model.DiagnosticMetadata
import dev.journeyscope.core.model.JourneySpan
import dev.journeyscope.core.model.JourneyTrace
import dev.journeyscope.core.model.MetadataValue
import dev.journeyscope.core.model.SpanStatus
import dev.journeyscope.core.model.TraceStatus
import dev.journeyscope.ui.model.BreakdownOrder
import dev.journeyscope.ui.model.DurationBreakdownUi
import dev.journeyscope.ui.model.DurationSegmentUi
import dev.journeyscope.ui.model.InsightUi
import dev.journeyscope.ui.model.JourneyCategory
import dev.journeyscope.ui.model.JourneyDetailUi
import dev.journeyscope.ui.model.JourneyListItemUi
import dev.journeyscope.ui.model.MetadataRowUi
import dev.journeyscope.ui.model.RowState
import dev.journeyscope.ui.model.StatusKind
import dev.journeyscope.ui.model.StatusUi
import dev.journeyscope.ui.model.TimelineRowUi
import dev.journeyscope.ui.model.displayName
import dev.journeyscope.ui.model.formatDuration
import dev.journeyscope.ui.model.percentOf
import dev.journeyscope.ui.model.spokenDuration
import dev.journeyscope.ui.model.toGlyph
import dev.journeyscope.ui.model.toJourneyCategory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

public data class InspectorThresholds(
    val slowJourney: Duration = 2_000.milliseconds,
    val slowSpan: Duration = 500.milliseconds,
) {
    public companion object {
        public fun from(config: JourneyScopeConfig): InspectorThresholds =
            InspectorThresholds(slowJourney = config.slowJourneyThreshold)
    }
}

private const val MAX_INDENT_DEPTH = 3
private const val HIDDEN_VALUE = "Hidden by display policy"

internal class TraceMapper(
    private val thresholds: InspectorThresholds,
    private val policy: MetadataDisplayPolicy,
    private val insightProvider: JourneyInsightProvider,
) {
    fun listItem(trace: JourneyTrace, nowNanos: Long): JourneyListItemUi {
        val timing = trace.timing(nowNanos)
        val breakdown = breakdown(trace, timing.duration.inWholeNanoseconds, nowNanos)
        val status = status(trace, timing.duration)
        return JourneyListItemUi(
            id = trace.id.value,
            title = trace.name,
            subtitle = subtitle(trace),
            status = status,
            breakdown = breakdown,
            spokenDescription = "${trace.name}, ${status.label}, ${breakdown.spokenDescription}",
        )
    }

    fun detail(trace: JourneyTrace, nowNanos: Long): JourneyDetailUi {
        val timing = trace.timing(nowNanos)
        val totalNanos = timing.duration.inWholeNanoseconds
        val breakdown = breakdown(trace, totalNanos, nowNanos)
        val rows = rows(trace, timing.spans.associateBy { it.spanId.value }, nowNanos)
        return JourneyDetailUi(
            id = trace.id.value,
            title = "Journey: ${trace.name}",
            subtitle = subtitle(trace),
            status = status(trace, timing.duration),
            breakdown = breakdown,
            rows = rows,
            insights = insights(breakdown, rows),
            failureLabel = trace.failure?.let { failureText(it.type, it.message) },
            droppedItems = trace.droppedItems,
        )
    }

    private fun subtitle(trace: JourneyTrace): String {
        val source = (trace.metadata[MetadataDisplayPolicy.SOURCE_KEY] as? MetadataValue.Text)
            ?.value?.takeIf { policy.isVisible(MetadataDisplayPolicy.SOURCE_KEY) }
        return buildString {
            append("Trace #").append(trace.id.value)
            if (source != null) append(" · ").append(source)
        }
    }

    private fun status(trace: JourneyTrace, duration: Duration): StatusUi = when (trace.status) {
        TraceStatus.CREATED -> StatusUi(StatusKind.Created, "Not started")
        TraceStatus.RUNNING -> StatusUi(StatusKind.Running, "Running")
        TraceStatus.FAILED -> StatusUi(StatusKind.Failed, "Failed")
        TraceStatus.CANCELLED -> StatusUi(StatusKind.Cancelled, "Cancelled")
        TraceStatus.INTERRUPTED -> StatusUi(StatusKind.Interrupted, "Interrupted")
        TraceStatus.COMPLETED ->
            if (duration >= thresholds.slowJourney) StatusUi(StatusKind.Slow, "Slow Flow")
            else StatusUi(StatusKind.Completed, "Completed")
    }

    private fun breakdown(trace: JourneyTrace, totalNanos: Long, nowNanos: Long): DurationBreakdownUi {
        val intervals = trace.spans.map {
            SpanInterval(
                id = it.id.value,
                parentId = it.parentId?.value,
                category = it.category.toJourneyCategory(),
                startNanos = it.startNanos,
                endNanos = it.endNanos ?: (trace.endNanos ?: nowNanos),
                sequence = it.sequence,
            )
        }
        val traceEnd = trace.startNanos + totalNanos
        val byCategory = aggregateByCategory(intervals, trace.startNanos, traceEnd)
        val segments = BreakdownOrder.mapNotNull { category ->
            val nanos = byCategory[category] ?: 0L
            if (nanos <= 0L) null
            else DurationSegmentUi(
                category = category,
                nanos = nanos,
                label = formatDuration(nanos),
                percent = percentOf(nanos, totalNanos),
                weight = (nanos.toDouble() / totalNanos).toFloat(),
            )
        }
        val spoken = buildString {
            append("total ").append(spokenDuration(totalNanos))
            segments.forEach { append(", ").append(it.category.label).append(' ').append(it.percent).append(" percent") }
        }
        return DurationBreakdownUi(totalNanos, formatDuration(totalNanos), segments, spoken)
    }

    private fun insights(breakdown: DurationBreakdownUi, rows: List<TimelineRowUi>): List<InsightUi> {
        val input = JourneyBreakdown(
            total = breakdown.totalNanos.nanoseconds,
            entries = breakdown.segments.map { BreakdownEntry(it.category, it.nanos.nanoseconds) },
        )
        return insightProvider.insights(input).map { insight ->
            val target = insight.focusCategory?.let { focus ->
                rows.filter { it.category == focus && it.state != RowState.Event }.maxByOrNull { it.selfNanos }?.id
            }
            InsightUi(insight.title, insight.message, target)
        }
    }

    private fun rows(
        trace: JourneyTrace,
        timings: Map<String, dev.journeyscope.core.timing.SpanTiming>,
        nowNanos: Long,
    ): List<TimelineRowUi> {
        val depthOf = depths(trace.spans)
        val spanRows = trace.spans.map { span ->
            val timing = timings[span.id.value]
            val inclusive = timing?.inclusive?.inWholeNanoseconds
                ?: ((span.endNanos ?: nowNanos) - span.startNanos).coerceAtLeast(0L)
            val self = timing?.exclusive?.inWholeNanoseconds ?: inclusive
            SortableRow(span.startNanos, span.sequence, spanRow(trace, span, inclusive, self, depthOf[span.id.value] ?: 0))
        }
        val eventRows = trace.events.map { event ->
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
                    metadata = metadataRows(event.metadata),
                    spokenDescription = "${event.name}, event, ${spokenDuration(offset)} after start",
                ),
            )
        }
        return (spanRows + eventRows).sortedWith(compareBy({ it.nanos }, { it.sequence })).map { it.row }
    }

    private fun spanRow(trace: JourneyTrace, span: JourneySpan, inclusive: Long, self: Long, depth: Int): TimelineRowUi {
        val state = when (span.status) {
            SpanStatus.RUNNING -> RowState.Running
            SpanStatus.OK -> RowState.Ok
            SpanStatus.FAILED -> RowState.Failed
            SpanStatus.CANCELLED -> RowState.Cancelled
            SpanStatus.INTERRUPTED -> RowState.Interrupted
        }
        val bottleneck = self >= thresholds.slowSpan.inWholeNanoseconds
        val detail = detailText(span.metadata) ?: span.category.displayName()
        val failure = span.failure?.let { failureText(it.type, it.message) }
        val offset = (span.startNanos - trace.startNanos).coerceAtLeast(0L)
        val spoken = buildString {
            append(span.name).append(", ").append(detail).append(", took ").append(spokenDuration(inclusive))
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

    private fun failureText(type: String, message: String?): String =
        if (policy.showFailureMessages && !message.isNullOrBlank()) "$type: $message" else type

    private fun detailText(metadata: DiagnosticMetadata): String? =
        (metadata[MetadataDisplayPolicy.DETAIL_KEY] as? MetadataValue.Text)
            ?.value?.takeIf { policy.isVisible(MetadataDisplayPolicy.DETAIL_KEY) && it.isNotBlank() }

    private fun metadataRows(metadata: DiagnosticMetadata): List<MetadataRowUi> = metadata.entries
        .filterKeys { it != MetadataDisplayPolicy.DETAIL_KEY }
        .entries
        .sortedBy { it.key }
        .map { (key, value) ->
            if (policy.isVisible(key)) MetadataRowUi(key, value.display(), hidden = false)
            else MetadataRowUi(key, HIDDEN_VALUE, hidden = true)
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

    private class SortableRow(val nanos: Long, val sequence: Long, val row: TimelineRowUi)
}

private fun MetadataValue.display(): String = when (this) {
    is MetadataValue.Text -> value
    is MetadataValue.Integer -> value.toString()
    is MetadataValue.Decimal -> value.toString()
    is MetadataValue.Flag -> value.toString()
}
