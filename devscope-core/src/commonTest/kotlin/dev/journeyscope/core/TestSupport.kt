package dev.journeyscope.core

import dev.journeyscope.core.model.JourneySpan
import dev.journeyscope.core.model.JourneyTrace
import dev.journeyscope.core.recorder.InMemoryJourneyRecorder
import dev.journeyscope.core.timing.JourneyClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random
import kotlin.time.Duration

class FakeJourneyClock : JourneyClock {
    private val nanos = MutableStateFlow(0L)

    fun advance(by: Duration) = nanos.update { it + by.inWholeNanoseconds }

    override fun monotonicNanos(): Long = nanos.value

    override fun wallClockMillis(): Long = WALL_EPOCH + nanos.value / NANOS_PER_MILLI

    private companion object {
        const val WALL_EPOCH = 1_700_000_000_000L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}

class SequentialIds : IdGenerator {
    private val counter = MutableStateFlow(0L)

    override fun nextId(): String = counter.updateAndGetCompat().toString()

    private fun MutableStateFlow<Long>.updateAndGetCompat(): Long {
        while (true) {
            val current = value
            if (compareAndSet(current, current + 1)) return current + 1
        }
    }
}

fun testTracer(
    config: JourneyScopeConfig = JourneyScopeConfig(enabled = true),
    clock: FakeJourneyClock = FakeJourneyClock(),
    random: Random = Random.Default,
): JourneyTracer = JourneyTracer(
    config = config,
    recorder = InMemoryJourneyRecorder(config.maxTraces),
    clock = clock,
    idGenerator = SequentialIds(),
    random = random,
)

fun Journey.trace(): JourneyTrace = checkNotNull(snapshot()) { "journey is not recording" }

fun JourneyTrace.span(name: String): JourneySpan = spans.single { it.name == name }
