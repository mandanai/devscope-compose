package dev.journeyscope.core.timing

import kotlin.time.TimeSource

/** Injectable time source: monotonic for durations, wall clock for display. */

public interface JourneyClock {
    /** Origin is arbitrary; only differences are meaningful. */
    public fun monotonicNanos(): Long

    public fun wallClockMillis(): Long
}

public object SystemJourneyClock : JourneyClock {
    private val origin = TimeSource.Monotonic.markNow()

    override fun monotonicNanos(): Long = origin.elapsedNow().inWholeNanoseconds

    override fun wallClockMillis(): Long = currentWallClockMillis()
}

internal expect fun currentWallClockMillis(): Long
