package dev.journeyscope.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Decides which diagnostic data may be recorded: values under keys containing any of `sensitiveKeyFragments` (case-insensitive) are replaced by `replacement`.
 * Failure messages are dropped unless `includeFailureMessages` is true.
 */

public data class RedactionPolicy(
    val sensitiveKeyFragments: Set<String> = DEFAULT_SENSITIVE_FRAGMENTS,
    val replacement: String = "[REDACTED]",
    val includeFailureMessages: Boolean = false,
) {
    internal fun isSensitive(key: String): Boolean {
        val lowered = key.lowercase()
        return sensitiveKeyFragments.any { lowered.contains(it.lowercase()) }
    }

    public companion object {
        public val DEFAULT_SENSITIVE_FRAGMENTS: Set<String> =
            setOf(
                "password",
                "passwd",
                "secret",
                "token",
                "authorization",
                "auth",
                "cookie",
                "session",
                "apikey",
                "api_key",
                "credential",
                "ssn",
                "card",
                "cvv",
                "pin",
            )

        public val Default: RedactionPolicy = RedactionPolicy()
    }
}

/**
 * Immutable tracing configuration; everything is off or bounded by default, and invalid limits throw [IllegalArgumentException].
 * Once a trace hits `maxEventsPerTrace` or `maxEventsPerSecond`, further items are dropped and counted in `droppedItems`.
 */

public data class JourneyScopeConfig(
    val enabled: Boolean = false,
    val maxTraces: Int = 50,
    val maxEventsPerTrace: Int = 500,
    /** Stack traces are truncated to `maxMetadataSize` and recorded only when true. */
    val captureStackTraces: Boolean = false,
    val recordFailures: Boolean = true,
    val slowJourneyThreshold: Duration = 2.seconds,
    val samplingRate: Double = 1.0,
    /** Character budget per metadata set, applied after redaction. */
    val maxMetadataSize: Int = 1024,
    val redactionPolicy: RedactionPolicy = RedactionPolicy.Default,
    /** Null means no rate limit. */
    val maxEventsPerSecond: Int? = null,
) {
    init {
        require(maxTraces > 0) { "maxTraces must be positive" }
        require(maxEventsPerTrace > 0) { "maxEventsPerTrace must be positive" }
        require(maxMetadataSize >= 0) { "maxMetadataSize must not be negative" }
        require(samplingRate in 0.0..1.0) { "samplingRate must be within 0.0..1.0" }
        require(slowJourneyThreshold.isPositive()) { "slowJourneyThreshold must be positive" }
        require(maxEventsPerSecond == null || maxEventsPerSecond > 0) { "maxEventsPerSecond must be positive" }
    }

    public companion object {
        /** Pass the host's own debuggable flag; release builds stay disabled. */
        public fun debugOnly(isDebuggable: Boolean): JourneyScopeConfig = JourneyScopeConfig(enabled = isDebuggable)
    }
}
