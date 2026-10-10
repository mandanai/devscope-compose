package dev.journeyscope.ui.mapping

import dev.journeyscope.ui.model.JourneyCategory
import dev.journeyscope.ui.model.percentOf
import kotlin.time.Duration

public data class BreakdownEntry(val category: JourneyCategory, val duration: Duration)

public data class JourneyBreakdown(val total: Duration, val entries: List<BreakdownEntry>) {
    public fun shareOf(category: JourneyCategory): Double {
        val totalNanos = total.inWholeNanoseconds
        if (totalNanos <= 0L) return 0.0
        val part = entries.filter { it.category == category }.sumOf { it.duration.inWholeNanoseconds }
        return part.toDouble() / totalNanos
    }
}

public data class JourneyInsight(
    val title: String,
    val message: String,
    val focusCategory: JourneyCategory? = null,
)

public fun interface JourneyInsightProvider {
    public fun insights(breakdown: JourneyBreakdown): List<JourneyInsight>
}

/** Duration-share heuristic only; wording stays hedged because share alone cannot prove a cause. */
public class DefaultInsightProvider(private val dominantShare: Double = DEFAULT_DOMINANT_SHARE) : JourneyInsightProvider {
    override fun insights(breakdown: JourneyBreakdown): List<JourneyInsight> {
        val totalNanos = breakdown.total.inWholeNanoseconds
        if (totalNanos <= 0L) return emptyList()
        val dominant = breakdown.entries
            .groupBy { it.category }
            .mapValues { (_, entries) -> entries.sumOf { it.duration.inWholeNanoseconds } }
            .maxByOrNull { it.value }
            ?: return emptyList()
        if (dominant.value.toDouble() / totalNanos < dominantShare) return emptyList()
        val percent = percentOf(dominant.value, totalNanos)
        return listOf(JourneyInsight(TITLE, messageFor(dominant.key, percent), dominant.key.takeUnless { it == JourneyCategory.Idle }))
    }

    private fun messageFor(category: JourneyCategory, percent: Int): String = when (category) {
        JourneyCategory.Network ->
            "API accounts for approximately $percent% of the total journey duration. " +
                "Investigate the network and backend before optimizing the UI."
        JourneyCategory.Storage ->
            "Storage accounts for approximately $percent% of the total journey duration. " +
                "Review query cost and data volume before optimizing the UI."
        JourneyCategory.Render ->
            "Rendering accounts for approximately $percent% of the total journey duration. " +
                "Review layout and composition cost."
        JourneyCategory.UiVm ->
            "UI and view-model work accounts for approximately $percent% of the total journey duration. " +
                "Review main-thread work and state propagation."
        JourneyCategory.Other ->
            "Uncategorized work accounts for approximately $percent% of the total journey duration. " +
                "Add categories to the recorded spans to narrow this down."
        JourneyCategory.Idle ->
            "Approximately $percent% of the total journey duration is not covered by any recorded span. " +
                "Instrument the missing steps to see where the time goes."
    }

    public companion object {
        public const val DEFAULT_DOMINANT_SHARE: Double = 0.5
        public const val TITLE: String = "Diagnostic insight"
    }
}
