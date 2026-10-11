package dev.chayaphon.journeyscope.sample.bench

import dev.chayaphon.journeyscope.core.JourneyScopeConfig
import dev.chayaphon.journeyscope.core.JourneyTracer
import dev.chayaphon.journeyscope.core.model.SpanCategory
import dev.chayaphon.journeyscope.core.recorder.InMemoryJourneyRecorder
import dev.chayaphon.journeyscope.export.persistence.JourneyPersistence
import dev.chayaphon.journeyscope.export.policy.ExportPolicy
import dev.chayaphon.journeyscope.export.redaction.RedactionPipeline
import dev.chayaphon.journeyscope.export.storage.StoredFile
import dev.chayaphon.journeyscope.export.storage.TraceFileSystem
import kotlinx.coroutines.runBlocking
import kotlin.time.TimeSource

enum class BenchmarkMode { DISABLED, ENABLED, ENABLED_WITH_REDACTION, ENABLED_WITH_PERSISTENCE }

data class BenchmarkResult(
    val mode: BenchmarkMode,
    val nanosPerJourney: Long,
    val journeysRecorded: Int,
)

/** Caller-side cost of one journey with `spansPerJourney` spans and an event each; persistence work is excluded by design. */
object OverheadBenchmark {
    fun run(
        journeys: Int = 2_000,
        spansPerJourney: Int = 5,
        warmup: Int = 200,
    ): List<BenchmarkResult> =
        BenchmarkMode.entries.map { mode ->
            val (tracer, recorderSize, close) = setup(mode)
            repeat(warmup) { journey(tracer, spansPerJourney, mode) }
            val start = TimeSource.Monotonic.markNow()
            repeat(journeys) { journey(tracer, spansPerJourney, mode) }
            val elapsed = start.elapsedNow().inWholeNanoseconds
            val recorded = recorderSize()
            close()
            BenchmarkResult(mode, elapsed / journeys, recorded)
        }

    private fun journey(
        tracer: JourneyTracer,
        spans: Int,
        mode: BenchmarkMode,
    ) {
        val journey = tracer.startJourney("bench")
        repeat(spans) { i ->
            journey.trace("span-$i", SpanCategory.CUSTOM) { journey.recordEvent("event-$i") }
        }
        journey.finish()
        if (mode == BenchmarkMode.ENABLED_WITH_REDACTION) journey.snapshot()?.let { REDACTION.redact(it) }
    }

    private data class Setup(
        val tracer: JourneyTracer,
        val recorded: () -> Int,
        val close: () -> Unit,
    )

    private fun setup(mode: BenchmarkMode): Setup {
        val config = JourneyScopeConfig(enabled = mode != BenchmarkMode.DISABLED, maxTraces = MAX_TRACES)
        if (mode == BenchmarkMode.ENABLED_WITH_PERSISTENCE) {
            val persistence =
                JourneyPersistence.install(config, ExportPolicy(persistenceEnabled = true), fileSystem = {
                    DiscardingFileSystem
                }, isEnabled = { true })
            return Setup(JourneyTracer(config, persistence.recorder), { persistence.recorder.snapshot().size }) {
                runBlocking { persistence.flush() }
                persistence.close()
            }
        }
        val recorder = InMemoryJourneyRecorder(MAX_TRACES)
        return Setup(JourneyTracer(config, recorder), { recorder.snapshot().size }, {})
    }

    private val REDACTION = RedactionPipeline()
    private const val MAX_TRACES = 50

    private object DiscardingFileSystem : TraceFileSystem {
        override fun list(): List<StoredFile> = emptyList()

        override fun read(name: String): String? = null

        override fun writeAtomically(
            name: String,
            content: String,
        ) = Unit

        override fun delete(name: String): Boolean = false
    }
}
