package dev.journeyscope.ui

import dev.journeyscope.core.JourneyScopeConfig
import dev.journeyscope.core.JourneyTracer
import dev.journeyscope.core.model.JourneyTrace
import dev.journeyscope.core.model.SpanCategory
import dev.journeyscope.diagnostics.DiagnosticAnalyzer
import dev.journeyscope.ui.mapping.InspectorThresholds
import dev.journeyscope.ui.mapping.MetadataDisplayPolicy
import dev.journeyscope.ui.mapping.TraceMapper
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Measures the presentation mapping cost (not frame time) for large traces; backs docs/performance.md. */
class InspectorScalingMeasurementTest {
    @Test
    fun mappingCostGrowsPredictablyWithTraceSize() {
        val mapper = TraceMapper(InspectorThresholds(slowJourney = 1.seconds), MetadataDisplayPolicy.Default, DiagnosticAnalyzer())
        val report = StringBuilder("mapping (TraceMapper.detail), ms median over $RUNS runs\n")
        SIZES.forEach { rows ->
            val trace = trace(rows)
            repeat(WARMUP) { mapper.detail(trace, trace.endNanos ?: 0) }
            val samples =
                List(RUNS) {
                    val start = TimeSource.Monotonic.markNow()
                    val detail = mapper.detail(trace, trace.endNanos ?: 0)
                    assertTrue(detail.rows.isNotEmpty())
                    start.elapsedNow().inWholeMicroseconds
                }.sorted()
            report.appendLine("$rows spans+events: ${samples[RUNS / 2] / MICROS_PER_MILLI.toDouble()} ms")
        }
        println(report)
        File("build").mkdirs()
        File("build/inspector-mapping-report.txt").writeText(report.toString())
    }

    private fun trace(spansAndEvents: Int): JourneyTrace {
        val journey = JourneyTracer(JourneyScopeConfig(enabled = true, maxEventsPerTrace = spansAndEvents * 2 + 2)).startJourney("large")
        repeat(spansAndEvents / 2) { index ->
            journey.trace("span-$index", SpanCategory.CUSTOM) { journey.recordEvent("event-$index") }
        }
        journey.finish()
        return checkNotNull(journey.snapshot())
    }

    private companion object {
        val SIZES = listOf(100, 500, 2_000, 10_000)
        const val RUNS = 15
        const val WARMUP = 3
        const val MICROS_PER_MILLI = 1_000
    }
}
