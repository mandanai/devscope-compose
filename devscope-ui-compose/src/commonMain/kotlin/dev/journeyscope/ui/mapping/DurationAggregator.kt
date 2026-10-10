package dev.journeyscope.ui.mapping

import dev.journeyscope.ui.model.JourneyCategory

internal class SpanInterval(
    val id: String,
    val parentId: String?,
    val category: JourneyCategory,
    val startNanos: Long,
    val endNanos: Long,
    val sequence: Long,
)

private const val MAX_DEPTH = 64

/**
 * Sweep-line attribution: every instant of the trace is charged to exactly one category,
 * the deepest (then latest-started, then highest-sequence) active span. Instants with no
 * active span are charged to [JourneyCategory.Idle], so the result always sums to the trace length.
 */
internal fun aggregateByCategory(
    intervals: List<SpanInterval>,
    traceStart: Long,
    traceEnd: Long,
): Map<JourneyCategory, Long> {
    val result = LinkedHashMap<JourneyCategory, Long>()
    if (traceEnd <= traceStart) return result

    val parentOf = intervals.associate { it.id to it.parentId }
    val depthOf = HashMap<String, Int>(intervals.size)
    fun depth(id: String): Int {
        depthOf[id]?.let { return it }
        var depth = 0
        var cursor = parentOf[id]
        while (cursor != null && depth < MAX_DEPTH && cursor in parentOf) {
            depth++
            cursor = parentOf[cursor]
        }
        return depth.also { depthOf[id] = it }
    }

    val clipped = intervals
        .map { SpanInterval(it.id, it.parentId, it.category, maxOf(it.startNanos, traceStart), minOf(it.endNanos, traceEnd), it.sequence) }
        .filter { it.endNanos > it.startNanos }
        .sortedWith(compareBy({ it.startNanos }, { it.sequence }))

    val boundaries = (listOf(traceStart, traceEnd) + clipped.flatMap { listOf(it.startNanos, it.endNanos) })
        .distinct()
        .sorted()

    val active = ArrayList<SpanInterval>()
    var next = 0
    for (index in 0 until boundaries.size - 1) {
        val from = boundaries[index]
        val to = boundaries[index + 1]
        while (next < clipped.size && clipped[next].startNanos <= from) active += clipped[next++]
        active.removeAll { it.endNanos <= from }
        val owner = active.maxWithOrNull(
            compareBy<SpanInterval>({ depth(it.id) }, { it.startNanos }, { it.sequence }),
        )
        val category = owner?.category ?: JourneyCategory.Idle
        result[category] = (result[category] ?: 0L) + (to - from)
    }
    return result
}
