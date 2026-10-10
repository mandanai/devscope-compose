package dev.journeyscope.core.timing

import dev.journeyscope.core.model.JourneySpan
import dev.journeyscope.core.model.JourneyTrace
import dev.journeyscope.core.model.SpanId
import kotlin.time.Duration.Companion.nanoseconds

internal object TimingCalculator {
    fun compute(trace: JourneyTrace, nowNanos: Long): JourneyTiming {
        val traceEnd = trace.endNanos ?: nowNanos
        val knownIds = trace.spans.mapTo(HashSet()) { it.id }
        val childrenOf = trace.spans.groupBy { span -> span.parentId?.takeIf { it in knownIds } }
        val tree = SpanTree(childrenOf, nowNanos)

        val spanTimings = trace.spans.map { span ->
            val end = tree.endOf(span)
            val inclusive = (end - span.startNanos).coerceAtLeast(0)
            val covered = unionLength(tree.childrenOf(span.id).map { it.startNanos to tree.endOf(it) }, span.startNanos, end)
            SpanTiming(
                spanId = span.id,
                inclusive = inclusive.nanoseconds,
                exclusive = (inclusive - covered).coerceAtLeast(0).nanoseconds,
                isOpen = span.endNanos == null,
            )
        }

        val roots = tree.childrenOf(null)
        val topChain = tree.criticalChain(roots, traceEnd)
        return JourneyTiming(
            wallClockStartMillis = trace.startWallMillis,
            wallClockEndMillis = trace.endWallMillis,
            duration = (traceEnd - trace.startNanos).coerceAtLeast(0).nanoseconds,
            isComplete = trace.endNanos != null,
            spans = spanTimings,
            gaps = gaps(roots.map { it.startNanos to tree.endOf(it) }, trace.startNanos, traceEnd),
            criticalPath = topChain.flatMap(tree::pathOf),
            criticalPathDuration = topChain.sumOf { tree.endOf(it) - it.startNanos }.nanoseconds,
        )
    }

    private fun gaps(intervals: List<Pair<Long, Long>>, from: Long, to: Long): List<TimeGap> {
        if (intervals.isEmpty()) return emptyList()
        val gaps = ArrayList<TimeGap>()
        var cursor = from
        for ((start, end) in merge(intervals, from, to)) {
            if (start > cursor) gaps += TimeGap((cursor - from).nanoseconds, (start - cursor).nanoseconds)
            cursor = maxOf(cursor, end)
        }
        if (to > cursor) gaps += TimeGap((cursor - from).nanoseconds, (to - cursor).nanoseconds)
        return gaps
    }

    private fun unionLength(intervals: List<Pair<Long, Long>>, from: Long, to: Long): Long =
        merge(intervals, from, to).sumOf { (start, end) -> end - start }

    private fun merge(intervals: List<Pair<Long, Long>>, from: Long, to: Long): List<Pair<Long, Long>> {
        val clipped = intervals
            .map { (start, end) -> maxOf(start, from) to minOf(end, to) }
            .filter { (start, end) -> end > start }
            .sortedBy { it.first }
        val merged = ArrayList<Pair<Long, Long>>()
        for (interval in clipped) {
            val last = merged.lastOrNull()
            if (last != null && interval.first <= last.second) {
                merged[merged.lastIndex] = last.first to maxOf(last.second, interval.second)
            } else {
                merged += interval
            }
        }
        return merged
    }

    private class SpanTree(private val children: Map<SpanId?, List<JourneySpan>>, private val nowNanos: Long) {
        fun endOf(span: JourneySpan): Long = span.endNanos ?: nowNanos

        fun childrenOf(id: SpanId?): List<JourneySpan> = children[id].orEmpty()

        /** Walks backward from [cursorStart], always taking the latest-finishing span that ends before the cursor. */
        fun criticalChain(candidates: List<JourneySpan>, cursorStart: Long): List<JourneySpan> {
            val order = compareBy<JourneySpan> { endOf(it) }.thenBy { -it.startNanos }.thenBy { -it.sequence }
            val chain = ArrayList<JourneySpan>()
            var cursor = cursorStart
            while (true) {
                val current = cursor
                val next = candidates
                    .filter { endOf(it) <= current && endOf(it) > it.startNanos }
                    .maxWithOrNull(order) ?: break
                chain += next
                cursor = next.startNanos
            }
            return chain.reversed()
        }

        fun pathOf(span: JourneySpan): List<SpanId> =
            listOf(span.id) + criticalChain(childrenOf(span.id), endOf(span)).flatMap(::pathOf)
    }
}
