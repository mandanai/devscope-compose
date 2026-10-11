package dev.chayaphon.journeyscope.core.timing

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

internal actual fun currentWallClockMillis(): Long = (NSDate().timeIntervalSince1970 * MILLIS_PER_SECOND).toLong()

private const val MILLIS_PER_SECOND = 1000.0
