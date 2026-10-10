package dev.journeyscope.diagnostics

import dev.journeyscope.core.model.JourneySpan
import dev.journeyscope.core.model.JourneyTrace
import dev.journeyscope.core.model.SpanCategory
import dev.journeyscope.core.model.SpanId
import dev.journeyscope.core.timing.SpanTiming
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

public data class CategoryTiming(
    val category: SpanCategory,
    /** Wall-clock time charged to this category; the sum over categories plus untracked equals the total. */
    val attributed: Duration,
    /** Union of the category's span intervals; may exceed [attributed] when other work is deeper. */
    val busy: Duration,
    val attributedNanos: Long,
)

/** Concurrent, non-nested spans and how long they ran together. */
public data class SpanOverlap(
    val first: SpanId,
    val second: SpanId,
    val overlap: Duration,
)

/**
 * Attribution rule: every instant is charged to exactly one active span, the deepest, then the
 * latest-started, then the highest-sequence. Time with no active span is [untracked].
 * Nested spans are never double-counted; overlapping (non-nested) spans are reported in [overlaps].
 */
public class TimingAnalysis internal constructor(
    public val total: Duration,
    public val totalNanos: Long,
    public val isComplete: Boolean,
    public val categories: List<CategoryTiming>,
    public val untracked: Duration,
    public val untrackedNanos: Long,
    public val spans: List<SpanTiming>,
    public val operations: List<SpanId>,
    public val overlaps: List<SpanOverlap>,
    public val maxConcurrency: Int,
    public val slowestSpan: SpanId?,
    public val criticalPath: List<SpanId>?,
) {
    public val isSequential: Boolean get() = overlaps.isEmpty()

    public fun share(category: SpanCategory): Double = if (totalNanos <= 0L) 0.0 else (categories.firstOrNull { it.category == category }?.attributedNanos ?: 0L).toDouble() / totalNanos
}

private const val MAX_DEPTH = 64

/** Open spans are measured up to [nowNanos]; [TimingAnalysis.isComplete] is false when the trace has not ended. */
public fun analyzeTiming(
    trace: JourneyTrace,
    nowNanos: Long = trace.lastObservedNanos,
): TimingAnalysis {
    val start = trace.startNanos
    val end = trace.endNanos ?: nowNanos
    val total = (end - start).coerceAtLeast(0)
    val coreTiming = trace.timing(nowNanos)

    val ids = trace.spans.associateBy { it.id }
    val depthCache = HashMap<SpanId, Int>()

    fun depth(span: JourneySpan): Int =
        depthCache.getOrPut(span.id) {
            var depth = 0
            var cursor = span.parentId
            while (cursor != null && depth < MAX_DEPTH && cursor in ids) {
                depth++
                cursor = ids.getValue(cursor).parentId
            }
            depth
        }

    fun isAncestor(
        ancestor: SpanId,
        of: JourneySpan,
    ): Boolean {
        var cursor = of.parentId
        var hops = 0
        while (cursor != null && hops++ < MAX_DEPTH) {
            if (cursor == ancestor) return true
            cursor = ids[cursor]?.parentId
        }
        return false
    }

    class Clipped(
        val span: JourneySpan,
        val from: Long,
        val to: Long,
    )
    val clipped =
        trace.spans
            .map { Clipped(it, maxOf(it.startNanos, start), minOf(it.endNanos ?: end, end)) }
            .filter { it.to > it.from }
            .sortedWith(compareBy({ it.from }, { it.span.sequence }))

    val boundaries = (listOf(start, end) + clipped.flatMap { listOf(it.from, it.to) }).distinct().sorted()
    val attributed = LinkedHashMap<SpanCategory, Long>()
    var untracked = 0L
    var maxConcurrency = 0
    if (total > 0) {
        val active = ArrayList<Clipped>()
        var next = 0
        for (index in 0 until boundaries.size - 1) {
            val from = boundaries[index]
            val to = boundaries[index + 1]
            while (next < clipped.size && clipped[next].from <= from) active += clipped[next++]
            active.removeAll { it.to <= from }
            val owner = active.maxWithOrNull(compareBy<Clipped>({ depth(it.span) }, { it.from }, { it.span.sequence }))
            if (owner == null) {
                untracked += to - from
            } else {
                attributed[owner.span.category] = (attributed[owner.span.category] ?: 0L) + (to - from)
            }
            val branches = active.count { parent -> active.none { it !== parent && it.span.parentId == parent.span.id } }
            maxConcurrency = maxOf(maxConcurrency, branches)
        }
    }

    val busy = HashMap<SpanCategory, Long>()
    clipped.groupBy { it.span.category }.forEach { (category, items) ->
        var covered = 0L
        var cursor = Long.MIN_VALUE
        for (item in items.sortedBy { it.from }) {
            val from = maxOf(item.from, cursor)
            if (item.to > from) covered += item.to - from
            cursor = maxOf(cursor, item.to)
        }
        busy[category] = covered
    }

    val overlaps =
        buildList {
            for (i in clipped.indices) {
                for (j in i + 1 until clipped.size) {
                    val a = clipped[i]
                    val b = clipped[j]
                    if (isAncestor(a.span.id, b.span) || isAncestor(b.span.id, a.span)) continue
                    val overlap = minOf(a.to, b.to) - maxOf(a.from, b.from)
                    if (overlap > 0) add(SpanOverlap(a.span.id, b.span.id, overlap.nanoseconds))
                }
            }
        }

    val slowest =
        coreTiming.spans
            .filter { !it.isOpen && it.exclusive > Duration.ZERO }
            .maxByOrNull { it.exclusive }
            ?.spanId

    return TimingAnalysis(
        total = total.nanoseconds,
        totalNanos = total,
        isComplete = trace.endNanos != null,
        categories =
            attributed
                .map { (category, nanos) ->
                    CategoryTiming(category, nanos.nanoseconds, (busy[category] ?: 0L).nanoseconds, nanos)
                }.sortedByDescending { it.attributedNanos },
        untracked = untracked.nanoseconds,
        untrackedNanos = untracked,
        spans = coreTiming.spans,
        operations = trace.spans.sortedWith(compareBy({ it.startNanos }, { it.sequence })).map { it.id },
        overlaps = overlaps,
        maxConcurrency = maxConcurrency,
        slowestSpan = slowest,
        criticalPath = if (overlaps.isEmpty()) coreTiming.criticalPath else null,
    )
}
