package dev.chayaphon.journeyscope.ui

import dev.chayaphon.journeyscope.diagnostics.DiagnosticAnalyzer
import dev.chayaphon.journeyscope.diagnostics.DiagnosticConfig
import dev.chayaphon.journeyscope.ui.demo.DemoJourneys
import dev.chayaphon.journeyscope.ui.mapping.InspectorThresholds
import dev.chayaphon.journeyscope.ui.mapping.MetadataDisplayPolicy
import dev.chayaphon.journeyscope.ui.mapping.TraceMapper
import dev.chayaphon.journeyscope.ui.model.JourneyCategory
import dev.chayaphon.journeyscope.ui.model.RowState
import dev.chayaphon.journeyscope.ui.model.StatusKind
import dev.chayaphon.journeyscope.ui.model.formatDuration
import dev.chayaphon.journeyscope.ui.model.groupThousands
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private const val MS = 1_000_000L

private fun mapper(policy: MetadataDisplayPolicy = MetadataDisplayPolicy.Default) = TraceMapper(InspectorThresholds(slowJourney = 1.seconds), policy, DiagnosticAnalyzer(DiagnosticConfig(slowJourney = 1.seconds)))

class TraceMapperTest {
    private val detail = mapper().detail(DemoJourneys.addToCart(), DemoJourneys.NOW_NANOS)

    @Test
    fun addToCartShowsAllEightStagesInOrder() {
        assertEquals(
            listOf(
                "Button Click",
                "ViewModel Action",
                "UseCase Execute",
                "API Request",
                "Business Validation",
                "Database Update",
                "UI State Emission",
                "UI Render Checkpoint",
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
            mapOf(
                JourneyCategory.UiVm to 65L,
                JourneyCategory.Network to 940L,
                JourneyCategory.Storage to 150L,
                JourneyCategory.Render to 110L,
            ),
            byCategory,
        )
    }

    @Test
    fun networkDominanceDiagnosticReports743PercentWithRelatedRow() {
        val diagnostic = detail.diagnostics.single { it.ruleId == "timing.dominant" }
        assertTrue("approximately 74.3% of this sequential journey" in diagnostic.evidence.first())
        assertEquals(listOf(detail.rows.first { it.title == "API Request" }.id), diagnostic.related.map { it.rowId })
        assertTrue(diagnostic.inference.orEmpty().isNotBlank())
        assertTrue(diagnostic.nextSteps.isNotEmpty())
    }

    @Test
    fun segmentPercentagesSumToOneHundred() {
        assertEquals(100, detail.breakdown.segments.sumOf { it.percent })
        assertEquals(
            74,
            detail.breakdown.segments
                .first { it.category == JourneyCategory.Network }
                .percent,
        )
    }

    @Test
    fun timingSectionShowsOneDecimalPercentAndSequentialCriticalPath() {
        val network = detail.timing.rows.first { it.category == JourneyCategory.Network }
        assertEquals("74.3%", network.percentLabel)
        assertEquals(null, detail.timing.overlapNote)
        assertTrue(detail.timing.criticalPathLabel.startsWith("Critical path:"))
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
        assertEquals(
            JourneyCategory.Idle,
            empty.breakdown.segments
                .single()
                .category,
        )
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
        assertEquals(
            1_000,
            large.rows
                .map { it.id }
                .toSet()
                .size,
        )
        assertEquals(large.breakdown.totalNanos, large.breakdown.segments.sumOf { it.nanos })
    }

    @Test
    fun nonAllowlistedMetadataIsHidden() {
        val row = detail.rows.first()
        val thread = row.metadata.single { it.key == "thread" }
        assertTrue(thread.hidden)
        assertFalse(
            mapper(MetadataDisplayPolicy.ShowAll)
                .detail(DemoJourneys.addToCart(), 0)
                .rows
                .first()
                .metadata
                .single {
                    it.key ==
                        "thread"
                }.hidden,
        )
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
    fun balancedJourneyHasNoDominantDiagnostic() {
        val strict =
            TraceMapper(
                InspectorThresholds(slowJourney = 1.seconds),
                MetadataDisplayPolicy.Default,
                DiagnosticAnalyzer(DiagnosticConfig(dominantShare = 0.99)),
            )
        val detail = strict.detail(DemoJourneys.addToCart(), DemoJourneys.NOW_NANOS)
        assertTrue(detail.diagnostics.none { it.ruleId == "timing.dominant" })
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

class DiagnosticMappingTest {
    private val mapper =
        TraceMapper(
            InspectorThresholds(slowJourney = 1.seconds),
            MetadataDisplayPolicy.Default,
            DiagnosticAnalyzer(DiagnosticConfig(slowJourney = 1.seconds, expectedSequences = listOf(DemoJourneys.addToCartCompletion))),
            listOf(DemoJourneys.addToCartCompletion),
        )

    @Test
    fun overlappingRequestsAreFlaggedAndNeverExceedTheTotal() {
        val detail = mapper.detail(DemoJourneys.parallelRequests(), DemoJourneys.NOW_NANOS)

        assertEquals(100, detail.breakdown.segments.sumOf { it.percent })
        assertTrue(detail.breakdown.segments.sumOf { it.nanos } <= detail.breakdown.totalNanos)
        assertTrue(
            detail.timing.overlapNote
                .orEmpty()
                .contains("overlapping"),
        )
        assertTrue(detail.timing.criticalPathLabel.startsWith("Critical path unavailable"))
    }

    @Test
    fun missingCheckpointProducesAWarningWithEvidence() {
        val detail = mapper.detail(DemoJourneys.addToCartWithoutCheckpoint(), DemoJourneys.NOW_NANOS)

        val missing = detail.diagnostics.single { it.ruleId == "journey.checkpoint_missing" }
        assertTrue(missing.evidence.any { "UI render checkpoint" in it })
        assertEquals(1, detail.sequences.single().missingCount)
    }

    @Test
    fun expectationDoesNotApplyToUnrelatedJourneys() {
        val detail = mapper.detail(DemoJourneys.parallelRequests(), DemoJourneys.NOW_NANOS)
        assertTrue(detail.diagnostics.none { it.ruleId == "journey.checkpoint_missing" })
        assertTrue(detail.sequences.isEmpty())
    }
}
