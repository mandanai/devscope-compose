package dev.chayaphon.journeyscope.diagnostics

import dev.chayaphon.journeyscope.core.model.SpanCategory
import dev.chayaphon.journeyscope.core.model.SpanId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimingAnalysisTest {
    @Test
    fun sequentialJourneyAttributesNetworkAt743Percent() {
        val timing = analyzeTiming(addToCartDemo())

        assertEquals(1265, timing.total.inWholeMilliseconds)
        val network = timing.categories.first { it.category == SpanCategory.NETWORK }
        assertEquals(940, network.attributed.inWholeMilliseconds)
        assertEquals("74.3%", Percentages.format(network.attributedNanos, timing.totalNanos))
        assertEquals(
            150,
            timing.categories
                .first { it.category == SpanCategory.DATABASE }
                .attributed.inWholeMilliseconds,
        )
        assertEquals(
            110,
            timing.categories
                .first { it.category == SpanCategory.UI_RENDER }
                .attributed.inWholeMilliseconds,
        )
        assertEquals(timing.totalNanos, timing.categories.sumOf { it.attributedNanos } + timing.untrackedNanos)
        assertTrue(timing.isSequential)
        assertEquals(1, timing.maxConcurrency)
    }

    @Test
    fun nestedSpansAreNotDoubleCounted() {
        val trace =
            TraceBuilder()
                .span("repo", SpanCategory.REPOSITORY, 0, 100)
                .span("api", SpanCategory.NETWORK, 10, 60, parent = "repo")
                .span("db", SpanCategory.DATABASE, 75, 20, parent = "repo")
                .build()

        val timing = analyzeTiming(trace)

        assertEquals(100, timing.categories.sumOf { it.attributed.inWholeMilliseconds })
        assertEquals(
            20,
            timing.categories
                .first { it.category == SpanCategory.REPOSITORY }
                .attributed.inWholeMilliseconds,
        )
        assertTrue(timing.isSequential)
        assertEquals(
            20,
            timing.spans
                .first { it.spanId == SpanId("repo") }
                .exclusive.inWholeMilliseconds,
        )
    }

    @Test
    fun overlappingSpansNeverExceedTotalAndAreFlagged() {
        val trace =
            TraceBuilder()
                .span("a", SpanCategory.NETWORK, 0, 500)
                .span("b", SpanCategory.NETWORK, 200, 500)
                .build()

        val timing = analyzeTiming(trace)

        assertEquals(700, timing.total.inWholeMilliseconds)
        assertEquals(
            700,
            timing.categories
                .single()
                .attributed.inWholeMilliseconds,
        )
        assertEquals(
            700,
            timing.categories
                .single()
                .busy.inWholeMilliseconds,
        )
        assertFalse(timing.isSequential)
        assertEquals(1, timing.overlaps.size)
        assertEquals(
            300,
            timing.overlaps
                .single()
                .overlap.inWholeMilliseconds,
        )
        assertEquals(2, timing.maxConcurrency)
        assertNull(timing.criticalPath)
    }

    @Test
    fun concurrentNetworkRequestsAreChargedOnceToWallClock() {
        val trace =
            TraceBuilder()
                .span("a", SpanCategory.NETWORK, 0, 400)
                .span("b", SpanCategory.NETWORK, 0, 400)
                .span("c", SpanCategory.NETWORK, 0, 400)
                .build()

        val timing = analyzeTiming(trace)

        assertEquals(
            400,
            timing.categories
                .single()
                .attributed.inWholeMilliseconds,
        )
        assertEquals(3, timing.overlaps.size)
        assertEquals(3, timing.maxConcurrency)
    }

    @Test
    fun sequentialJourneyExposesCriticalPath() {
        assertNotNull(analyzeTiming(addToCartDemo()).criticalPath)
    }

    @Test
    fun uncoveredTimeIsUntracked() {
        val trace = TraceBuilder().span("a", SpanCategory.NETWORK, 0, 100).build(totalMs = 200)
        val timing = analyzeTiming(trace)
        assertEquals(100, timing.untracked.inWholeMilliseconds)
    }

    @Test
    fun slowestSpanUsesExclusiveTime() {
        val trace =
            TraceBuilder()
                .span("wrapper", SpanCategory.REPOSITORY, 0, 1000)
                .span("api", SpanCategory.NETWORK, 0, 950, parent = "wrapper")
                .build()
        assertEquals(SpanId("api"), analyzeTiming(trace).slowestSpan)
    }

    @Test
    fun percentagesSumToOneHundred() {
        val parts = listOf(65L, 940L, 150L, 110L)
        val rounded = Percentages.distribute(parts, 1265)
        assertEquals(100, rounded.sum())
        assertEquals(listOf(5, 74, 12, 9), rounded)
        assertEquals(100, Percentages.distribute(listOf(1L, 1L, 1L), 3).sum())
        assertEquals("33.3%", Percentages.format(1, 3))
        assertEquals("0.1%", Percentages.format(1, 1000))
    }
}
