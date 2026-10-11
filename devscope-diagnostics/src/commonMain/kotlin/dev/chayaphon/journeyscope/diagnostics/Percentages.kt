package dev.chayaphon.journeyscope.diagnostics

/** Rounding helpers so displayed percentages stay consistent. */
public object Percentages {
    /** Whole-number percentages of [total] that sum to exactly 100 (largest-remainder method). */
    public fun distribute(
        parts: List<Long>,
        total: Long,
    ): List<Int> {
        if (total <= 0L || parts.isEmpty()) return parts.map { 0 }
        val floors = parts.map { (it * 100) / total }
        val remainders = parts.mapIndexed { index, part -> index to ((part * 100) % total) }
        var missing = (100 - floors.sum()).toInt().coerceAtLeast(0)
        val result = floors.map { it.toInt() }.toMutableList()
        for ((index, _) in remainders.sortedWith(compareByDescending<Pair<Int, Long>> { it.second }.thenBy { it.first })) {
            if (missing == 0) break
            if (parts[index] > 0) {
                result[index]++
                missing--
            }
        }
        return result
    }

    /** One-decimal percentage, half-up, e.g. 940 of 1265 -> "74.3%". */
    public fun format(
        part: Long,
        total: Long,
    ): String {
        if (total <= 0L) return "0.0%"
        val tenths = (part * 1000 + total / 2) / total
        return "${tenths / 10}.${tenths % 10}%"
    }
}
