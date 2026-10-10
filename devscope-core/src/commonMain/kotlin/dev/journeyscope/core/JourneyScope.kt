package dev.journeyscope.core

import kotlin.concurrent.Volatile

public object JourneyScope {
    public const val VERSION: String = "0.1.0-SNAPSHOT"

    @Volatile
    public var isEnabled: Boolean = false
}
