package dev.journeyscope.core.model

import dev.journeyscope.core.timing.JourneyTiming
import dev.journeyscope.core.timing.TimingCalculator
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/** Sanitized failure; `message` and `stackTrace` stay null unless the redaction policy and config allow them. */

public data class FailureInfo(
    val type: String,
    val message: String? = null,
    val stackTrace: String? = null,
)

/** Point-in-time marker; `atNanos` is monotonic, `atWallMillis` is for display only. */

public data class JourneyEvent(
    val id: EventId,
    val name: String,
    val category: SpanCategory,
    val sequence: Long,
    val atNanos: Long,
    val atWallMillis: Long,
    val spanId: SpanId?,
    val metadata: DiagnosticMetadata,
)

/** Timed unit of work; `endNanos` is null while open, and `endedBySystem` marks spans closed by journey termination. */

public data class JourneySpan(
    val id: SpanId,
    val parentId: SpanId?,
    val name: String,
    val category: SpanCategory,
    val sequence: Long,
    val startNanos: Long,
    val startWallMillis: Long,
    val endNanos: Long?,
    val status: SpanStatus,
    val failure: FailureInfo?,
    val endedBySystem: Boolean,
    val metadata: DiagnosticMetadata,
) {
    public val isOpen: Boolean get() = endNanos == null
}

public data class JourneySummary(
    val name: String,
    val status: TraceStatus,
    val duration: Duration,
    val spanCount: Int,
    val eventCount: Int,
    val failedSpanCount: Int,
    val droppedItems: Int,
    val isSlow: Boolean,
    val criticalPathDuration: Duration,
    val failureType: String?,
)

/** Immutable snapshot of a journey; `droppedItems` counts spans and events discarded by configured limits. */

public data class JourneyTrace(
    val id: TraceId,
    val name: String,
    val status: TraceStatus,
    val startNanos: Long,
    val startWallMillis: Long,
    val endNanos: Long?,
    val endWallMillis: Long?,
    val spans: List<JourneySpan>,
    val events: List<JourneyEvent>,
    val failure: FailureInfo?,
    val metadata: DiagnosticMetadata,
    val droppedItems: Int,
    val summary: JourneySummary?,
) {
    /** Alias of [id], the value sent in propagation headers. */
    public val correlationId: TraceId get() = id

    /** Latest timestamp in the trace; stands in for "now" when the trace is still running. */

    public val lastObservedNanos: Long
        get() {
            var latest = maxOf(startNanos, endNanos ?: startNanos)
            for (span in spans) latest = maxOf(latest, span.startNanos, span.endNanos ?: span.startNanos)
            for (event in events) latest = maxOf(latest, event.atNanos)
            return latest
        }

    public fun timing(nowNanos: Long = lastObservedNanos): JourneyTiming = TimingCalculator.compute(this, nowNanos)

    /** Position on the trace timeline; deliberately not an operation duration. */
    public fun offsetFromStart(event: JourneyEvent): Duration = (event.atNanos - startNanos).coerceAtLeast(0).nanoseconds
}
