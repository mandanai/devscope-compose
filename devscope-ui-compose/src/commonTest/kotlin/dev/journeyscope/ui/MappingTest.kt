package dev.journeyscope.ui

import dev.journeyscope.ui.demo.DemoJourneys
import dev.journeyscope.ui.mapping.DefaultInsightProvider
import dev.journeyscope.ui.mapping.InspectorThresholds
import dev.journeyscope.ui.mapping.MetadataDisplayPolicy
import dev.journeyscope.ui.mapping.SpanInterval
import dev.journeyscope.ui.mapping.TraceMapper
import dev.journeyscope.ui.mapping.aggregateByCategory
import dev.journeyscope.ui.model.JourneyCategory
import dev.journeyscope.ui.model.RowState
import dev.journeyscope.ui.model.StatusKind
import dev.journeyscope.ui.model.formatDuration
import dev.journeyscope.ui.model.groupThousands
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private const val MS = 1_000_000L

private fun mapper(policy: MetadataDisplayPolicy = MetadataDisplayPolicy.Default) =
    TraceMapper(InspectorThresholds(slowJourney = 1.seconds), policy, DefaultInsightProvider())

class DurationAggregatorTest {
    private fun interval(id: String, category: JourneyCategory, start: Long, end: Long, parent: String? = null, seq: Long = 0) =
        SpanInterval(id, parent, category, start * MS, end * MS, seq)

    @Test
    fun sequentialSpansSumToTraceLength() {
        val result = aggregateByCategory(
            listOf(
                interval("a", JourneyCategory.UiVm, 0, 10),
                interval("b", JourneyCategory.Network, 10, 110),
            ),
            0, 110 * MS,
        )
        assertEquals(10 * MS, result[JourneyCategory.UiVm])
        assertEquals(100 * MS, result[JourneyCategory.Network])
        assertEquals(110 * MS, result.values.sum())
    }

    @Test
    fun overlappingSiblingsAreNeverDoubleCounted() {
        val result = aggregateByCategory(
            listOf(
                interval("net", JourneyCategory.Network, 0, 100, seq = 0),
                interval("db", JourneyCategory.Storage, 50, 150, seq = 1),
            ),
            0, 150 * MS,
        )
        assertEquals(150 * MS, result.values.sum())
        assertEquals(50 * MS, result[JourneyCategory.Network])
        assertEquals(100 * MS, result[JourneyCategory.Storage])
    }

    @Test
    fun childOwnsItsWindowInsideParent() {
        val result = aggregateByCategory(
            listOf(
                interval("parent", JourneyCategory.UiVm, 0, 100),
                interval("child", JourneyCategory.Network, 20, 80, parent = "parent"),
            ),
            0, 100 * MS,
        )
        assertEquals(60 * MS, result[JourneyCategory.Network])
        assertEquals(40 * MS, result[JourneyCategory.UiVm])
    }

    @Test
    fun uncoveredTimeIsIdle() {
        val result = aggregateByCategory(listOf(interval("a", JourneyCategory.Render, 10, 20)), 0, 50 * MS)
        assertEquals(40 * MS, result[JourneyCategory.Idle])
        assertEquals(10 * MS, result[JourneyCategory.Render])
    }

    @Test
    fun spansOutsideTheTraceAreClipped() {
        val result = aggregateByCategory(listOf(interval("a", JourneyCategory.Render, -10, 500)), 0, 100 * MS)
        assertEquals(mapOf(JourneyCategory.Render to 100 * MS), result)
    }
}

class TraceMapperTest {
    private val detail = mapper().detail(DemoJourneys.addToCart(), DemoJourneys.NOW_NANOS)

    @Test
    fun addToCartShowsAllEightStagesInOrder() {
        assertEquals(
            listOf(
                "Button Click", "ViewModel Action", "UseCase Execute", "API Request",
                "Business Validation", "Database Update", "UI State Emission", "UI Render Checkpoint",
            ),
            detail.rows.map { it.title },
        )
        assertEquals(listOf("0 ms", "12 ms", "34 ms", "940 ms", "4 ms", "150 ms", "15 ms", "110 ms"), detail.rows.map { it.durationLabel })
    }

    @Test
    fun totalIsOneThousandTwoHundredSixtyFiveMs() {
        assertEquals("1,265 ms", detail.breakdown.totalLabel)
        assertEquals("Journey: Add to Cart", detail.title)
        assertEquals("Trace #CART-1024 · Demo data", detail.subtitle)
        assertEquals(StatusKind.Slow, detail.status.kind)
        assertEquals("Slow Flow", detail.status.label)
    }

    @Test
    fun networkRowIsTheOnlyBottleneck() {
        assertEquals(listOf("API Request"), detail.rows.filter { it.isBottleneck }.map { it.title })
    }

    @Test
    fun breakdownMatchesCategoryTotals() {
        val byCategory = detail.breakdown.segments.associate { it.category to it.nanos / MS }
        assertEquals(
            mapOf(JourneyCategory.UiVm to 65L, JourneyCategory.Network to 940L, JourneyCategory.Storage to 150L, JourneyCategory.Render to 110L),
            byCategory,
        )
    }

    @Test
    fun insightPercentageIsComputedFromData() {
        val insight = detail.insights.single()
        assertEquals("Diagnostic insight", insight.title)
        assertEquals(
            "API accounts for approximately 74% of the total journey duration. Investigate the network and backend before optimizing the UI.",
            insight.message,
        )
        assertEquals(detail.rows.first { it.title == "API Request" }.id, insight.targetRowId)
    }

    @Test
    fun fastJourneyIsCompletedNotSlow() {
        val fast = mapper().detail(DemoJourneys.openProduct(), DemoJourneys.NOW_NANOS)
        assertEquals(StatusKind.Completed, fast.status.kind)
    }

    @Test
    fun failedJourneyMarksFailedRow() {
        val failed = mapper().detail(DemoJourneys.checkoutFailed(), DemoJourneys.NOW_NANOS)
        assertEquals(StatusKind.Failed, failed.status.kind)
        assertEquals(RowState.Failed, failed.rows.last().state)
        assertEquals("IOException", failed.rows.last().failureLabel)
        assertEquals("IOException", failed.failureLabel)
    }

    @Test
    fun failureMessageRequiresOptIn() {
        val shown = mapper(MetadataDisplayPolicy.ShowAll).detail(DemoJourneys.checkoutFailed(), DemoJourneys.NOW_NANOS)
        assertEquals("IOException: Service unavailable", shown.rows.last().failureLabel)
    }

    @Test
    fun runningJourneyMeasuresOpenSpansAgainstNow() {
        val running = mapper().detail(DemoJourneys.syncOrdersRunning(), DemoJourneys.NOW_NANOS)
        assertEquals(StatusKind.Running, running.status.kind)
        assertEquals(RowState.Running, running.rows.last().state)
        assertEquals("780 ms", running.rows.last().durationLabel)
        assertEquals("900 ms", running.breakdown.totalLabel)
    }

    @Test
    fun emptyJourneyIsAllUntracked() {
        val empty = mapper().detail(DemoJourneys.coldStartEmpty(), DemoJourneys.NOW_NANOS)
        assertTrue(empty.rows.isEmpty())
        assertEquals(listOf(JourneyCategory.Idle), empty.breakdown.segments.map { it.category })
        assertEquals(JourneyCategory.Idle, empty.breakdown.segments.single().category)
    }

    @Test
    fun eventsAppearInTimeline() {
        val row = mapper().detail(DemoJourneys.syncProfileRunning(), DemoJourneys.NOW_NANOS).rows.first { it.state == RowState.Event }
        assertEquals("Refresh Requested", row.title)
        assertEquals("+0 ms", row.durationLabel)
    }

    @Test
    fun largeJourneyKeepsStableUniqueKeys() {
        val large = mapper().detail(DemoJourneys.importCatalogLarge(), DemoJourneys.NOW_NANOS)
        assertEquals(1_000, large.rows.size)
        assertEquals(1_000, large.rows.map { it.id }.toSet().size)
        assertEquals(large.breakdown.totalNanos, large.breakdown.segments.sumOf { it.nanos })
    }

    @Test
    fun nonAllowlistedMetadataIsHidden() {
        val row = detail.rows.first()
        val thread = row.metadata.single { it.key == "thread" }
        assertTrue(thread.hidden)
        assertFalse(mapper(MetadataDisplayPolicy.ShowAll).detail(DemoJourneys.addToCart(), 0).rows.first().metadata.single { it.key == "thread" }.hidden)
    }

    @Test
    fun listItemCarriesSpokenSummary() {
        val item = mapper().listItem(DemoJourneys.addToCart(), DemoJourneys.NOW_NANOS)
        assertTrue(item.spokenDescription.contains("Slow Flow"))
        assertTrue(item.spokenDescription.contains("1265 milliseconds"))
    }
}

class InsightAndFormattingTest {
    @Test
    fun noInsightWithoutDominantCategory() {
        val detail = mapper().detail(DemoJourneys.openProduct(), DemoJourneys.NOW_NANOS)
        assertTrue(detail.insights.isNotEmpty())
        val balanced = DefaultInsightProvider(dominantShare = 0.99)
        assertTrue(balanced.insights(
            dev.journeyscope.ui.mapping.JourneyBreakdown(
                kotlin.time.Duration.parse("1s"),
                listOf(
                    dev.journeyscope.ui.mapping.BreakdownEntry(JourneyCategory.Network, kotlin.time.Duration.parse("500ms")),
                    dev.journeyscope.ui.mapping.BreakdownEntry(JourneyCategory.Render, kotlin.time.Duration.parse("500ms")),
                ),
            ),
        ).isEmpty())
    }

    @Test
    fun durationFormatting() {
        assertEquals("0 ms", formatDuration(0))
        assertEquals("<1 ms", formatDuration(100_000))
        assertEquals("1,265 ms", formatDuration(1_265 * MS))
        assertEquals("1,234,567", groupThousands(1_234_567))
        assertEquals("999", groupThousands(999))
    }

    @Test
    fun metadataPolicySupportsPrefixRules() {
        val policy = MetadataDisplayPolicy(visibleKeys = setOf("http.*", "returnCode"))
        assertTrue(policy.isVisible("http.status"))
        assertTrue(policy.isVisible("returnCode"))
        assertFalse(policy.isVisible("userId"))
    }
}
