package dev.chayaphon.journeyscope.core.timing

import dev.chayaphon.journeyscope.core.model.SpanId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** `exclusive` is `inclusive` minus time spent in child spans; open spans are measured up to "now". */

public data class SpanTiming(
    val spanId: SpanId,
    val inclusive: Duration,
    val exclusive: Duration,
    val isOpen: Boolean,
)

/** Time inside the trace not covered by any root span, e.g. dispatch or scheduling delay. */
public data class TimeGap(
    val offsetFromStart: Duration,
    val duration: Duration,
)

/** Derived timing of a trace; durations are monotonic, wall-clock fields are display only. */

public data class JourneyTiming(
    val wallClockStartMillis: Long,
    val wallClockEndMillis: Long?,
    val duration: Duration,
    val isComplete: Boolean,
    val spans: List<SpanTiming>,
    val gaps: List<TimeGap>,
    val criticalPath: List<SpanId>,
    val criticalPathDuration: Duration,
) {
    /** Display only: derived from wall clocks, which can jump. [duration] is the monotonic truth. */
    public val wallClockDuration: Duration?
        get() = wallClockEndMillis?.let { (it - wallClockStartMillis).milliseconds }
}
