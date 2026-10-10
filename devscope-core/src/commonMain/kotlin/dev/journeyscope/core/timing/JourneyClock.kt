package dev.journeyscope.core.timing

import kotlin.time.TimeSource

public interface JourneyClock {
    public fun monotonicNanos(): Long

    public fun wallClockMillis(): Long
}

public object SystemJourneyClock : JourneyClock {
    private val origin = TimeSource.Monotonic.markNow()

    override fun monotonicNanos(): Long = origin.elapsedNow().inWholeNanoseconds

    override fun wallClockMillis(): Long = currentWallClockMillis()
}

internal expect fun currentWallClockMillis(): Long
