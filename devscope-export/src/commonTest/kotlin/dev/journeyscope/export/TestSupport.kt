package dev.journeyscope.export

import dev.journeyscope.core.IdGenerator
import dev.journeyscope.core.JourneyScopeConfig
import dev.journeyscope.core.JourneyTracer
import dev.journeyscope.core.model.DiagnosticMetadata
import dev.journeyscope.core.model.JourneyTrace
import dev.journeyscope.core.model.SpanCategory
import dev.journeyscope.core.model.diagnosticMetadata
import dev.journeyscope.core.recorder.InMemoryJourneyRecorder
import dev.journeyscope.core.timing.JourneyClock
import dev.journeyscope.export.storage.StoredFile
import dev.journeyscope.export.storage.TraceFileSystem
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class FakeClock(
    var nanos: Long = 0,
    var wall: Long = 1_700_000_000_000L,
) : JourneyClock {
    override fun monotonicNanos(): Long = nanos

    override fun wallClockMillis(): Long = wall

    fun advance(by: Duration) {
        nanos += by.inWholeNanoseconds
        wall += by.inWholeMilliseconds
    }
}

class CountingIds(
    private val prefix: String = "n",
) : IdGenerator {
    private var counter = 0L

    override fun nextId(): String = "$prefix${counter++}"
}

class FakeFileSystem : TraceFileSystem {
    val files = LinkedHashMap<String, String>()
    var failWrites = false
    var writeCount = 0
    private var clock = 0L

    override fun list(): List<StoredFile> = files.map { (name, text) -> StoredFile(name, text.encodeToByteArray().size.toLong(), clock) }

    override fun read(name: String): String? = files[name]

    override fun writeAtomically(
        name: String,
        content: String,
    ) {
        if (failWrites) error("disk full")
        writeCount++
        files[name] = content
    }

    override fun delete(name: String): Boolean = files.remove(name) != null
}

val enabledConfig = JourneyScopeConfig(enabled = true)

fun sampleTrace(
    clock: FakeClock = FakeClock(),
    config: JourneyScopeConfig = enabledConfig,
    ids: String = "n",
    metadata: DiagnosticMetadata = DiagnosticMetadata.Empty,
): JourneyTrace {
    val tracer = JourneyTracer(config, InMemoryJourneyRecorder(config.maxTraces), clock, CountingIds(ids))
    val journey = tracer.startJourney("checkout", metadata)
    journey.trace("ViewModel.add", SpanCategory.VIEW_MODEL) { root ->
        clock.advance(10.milliseconds)
        journey.recordEvent("clicked", SpanCategory.UI_INTERACTION)
        journey.trace(
            "POST /cart",
            SpanCategory.NETWORK,
            diagnosticMetadata {
                put("http.method", "POST")
                put("http.path", "/cart")
                put("authorization", "Bearer abcdefghijklmnop")
                put("http.status", 200)
            },
            parent = root,
        ) {
            clock.advance(40.milliseconds)
        }
    }
    journey.finish()
    return journey.snapshot() ?: error("expected a recording journey")
}
