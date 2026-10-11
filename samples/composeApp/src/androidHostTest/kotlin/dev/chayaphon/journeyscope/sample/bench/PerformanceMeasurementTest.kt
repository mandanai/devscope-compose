package dev.chayaphon.journeyscope.sample.bench

import dev.chayaphon.journeyscope.core.JourneyScope
import dev.chayaphon.journeyscope.core.JourneyScopeConfig
import dev.chayaphon.journeyscope.core.JourneyTracer
import dev.chayaphon.journeyscope.core.model.JourneyTrace
import dev.chayaphon.journeyscope.core.model.SpanCategory
import dev.chayaphon.journeyscope.core.model.diagnosticMetadata
import dev.chayaphon.journeyscope.core.recorder.InMemoryJourneyRecorder
import dev.chayaphon.journeyscope.export.redaction.RedactionPipeline
import dev.chayaphon.journeyscope.export.schema.TraceReportWriter
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.TimeSource

/**
 * Host-JVM measurements behind docs/performance.md. Prints a table instead of asserting timings, which depend on the machine.
 * Results are also written to `build/performance-report.txt`.
 */
class PerformanceMeasurementTest {
    private val report = StringBuilder()

    @Test
    fun measure() {
        line("runtime: ${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}, ${System.getProperty("os.name")} ${System.getProperty("os.arch")}, cpus=${Runtime.getRuntime().availableProcessors()}")
        line("runs=$RUNS (after $WARMUP warm-up runs); values are median / p95")
        initialization()
        recording(enabled = true)
        recording(enabled = false)
        memoryAtLimits()
        exportDuration()
        println(report)
        File("build").mkdirs()
        File("build/performance-report.txt").writeText(report.toString())
    }

    private fun initialization() {
        val samples = sample { timed { repeat(INIT_ITERATIONS) { JourneyScope.configure(JourneyScopeConfig(enabled = true)) } } / INIT_ITERATIONS }
        JourneyScope.configure(JourneyScopeConfig())
        line("init (JourneyScope.configure, ns/call): ${samples.summary()}")
    }

    private fun recording(enabled: Boolean) {
        val label = if (enabled) "enabled" else "disabled"
        val sizes = if (enabled) listOf(DEFAULT_TRACE_OPS, SCALING_TRACE_OPS) else listOf(DEFAULT_TRACE_OPS)
        sizes.forEach { perTrace ->
            val events = sample { perOperation(enabled, perTrace) { journey -> journey.recordEvent("e", SpanCategory.CUSTOM) } }
            val spans = sample { perOperation(enabled, perTrace) { journey -> journey.trace("s", SpanCategory.CUSTOM) { } } }
            line("$label recordEvent, $perTrace ops/trace (ns/op): ${events.summary()}")
            line("$label span trace{}, $perTrace ops/trace (ns/op): ${spans.summary()}")
        }
    }

    private fun perOperation(
        enabled: Boolean,
        perTrace: Int,
        operation: (dev.chayaphon.journeyscope.core.Journey) -> Unit,
    ): Long {
        val tracer = tracer(enabled, maxEvents = perTrace * 2)
        val journeys = List(TOTAL_OPS / perTrace) { tracer.startJourney("bench") }
        val elapsed = timed { journeys.forEach { journey -> repeat(perTrace) { operation(journey) } } }
        return elapsed / TOTAL_OPS
    }

    private fun memoryAtLimits() {
        val config = JourneyScopeConfig(enabled = true)
        val baseline = usedHeap()
        val recorder = InMemoryJourneyRecorder(config.maxTraces)
        val tracer = JourneyTracer(config, recorder)
        repeat(config.maxTraces) { t -> fill(tracer, "journey-$t", config.maxEventsPerTrace) }
        val retained = usedHeap() - baseline
        assertEquals(config.maxTraces, recorder.snapshot().size)
        line("memory at limits (${config.maxTraces} traces x ${config.maxEventsPerTrace} events, 1 metadata entry each): ~${retained / KIB} KiB retained heap (single measurement, after System.gc)")
        check(recorder.snapshot().isNotEmpty())
    }

    private fun exportDuration() {
        val config = JourneyScopeConfig(enabled = true)
        val traces = traces(config)
        val pipeline = RedactionPipeline()
        var bytes = 0
        val samples =
            sample {
                timed { bytes = TraceReportWriter.write(traces.map { pipeline.redact(it) }).length }
            }
        line("export (redact + serialize ${traces.size} traces, ${traces.sumOf { it.events.size }} events -> $bytes chars) ms: ${samples.map { it / NANOS_PER_MILLI }.summary()}")
    }

    private fun traces(config: JourneyScopeConfig): List<JourneyTrace> {
        val recorder = InMemoryJourneyRecorder(config.maxTraces)
        val tracer = JourneyTracer(config, recorder)
        repeat(config.maxTraces) { t -> fill(tracer, "journey-$t", config.maxEventsPerTrace) }
        return recorder.snapshot()
    }

    private fun fill(
        tracer: JourneyTracer,
        name: String,
        events: Int,
    ) {
        val journey = tracer.startJourney(name)
        repeat(events) { journey.recordEvent("event-$it", SpanCategory.CUSTOM, diagnosticMetadata { put("index", "$it") }) }
        journey.finish()
    }

    private fun tracer(
        enabled: Boolean,
        maxEvents: Int,
    ) = JourneyTracer(JourneyScopeConfig(enabled = enabled, maxEventsPerTrace = maxEvents))

    private fun sample(block: () -> Long): List<Long> {
        repeat(WARMUP) { block() }
        return List(RUNS) { block() }
    }

    private fun timed(block: () -> Unit): Long {
        val start = TimeSource.Monotonic.markNow()
        block()
        return start.elapsedNow().inWholeNanoseconds
    }

    private fun List<Long>.summary(): String {
        val sorted = sorted()
        return "${sorted[sorted.size / 2]} / ${sorted[(sorted.size * P95).toInt().coerceAtMost(sorted.size - 1)]}"
    }

    private fun usedHeap(): Long {
        repeat(GC_ROUNDS) {
            System.gc()
            Thread.sleep(GC_PAUSE_MS)
        }
        return Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
    }

    private fun line(text: String) {
        report.appendLine(text)
    }

    private companion object {
        const val RUNS = 20
        const val WARMUP = 5
        const val TOTAL_OPS = 20_000
        const val DEFAULT_TRACE_OPS = 400
        const val SCALING_TRACE_OPS = 4_000
        const val INIT_ITERATIONS = 1_000
        const val P95 = 0.95
        const val KIB = 1024L
        const val NANOS_PER_MILLI = 1_000_000L
        const val GC_ROUNDS = 3
        const val GC_PAUSE_MS = 50L
    }
}
