package dev.chayaphon.journeyscope.core.network

import dev.chayaphon.journeyscope.core.Journey

/** What to do with a request made outside any journey; it is never attached to an unrelated journey. */
public sealed class NoJourneyPolicy {
    public data object Ignore : NoJourneyPolicy()

    /** Each such request becomes its own single-span trace. */
    public data object Standalone : NoJourneyPolicy()

    /** Every such request joins [journey] as a root-level span. */
    public class Explicit(
        public val journey: Journey,
    ) : NoJourneyPolicy()
}

/** Opt-in outbound correlation headers; sent only to [trustedHosts] (`host` or `*.suffix`). */
public class PropagationConfig(
    public val trustedHosts: Set<String>,
    public val traceIdHeader: String = "X-JourneyScope-Trace-Id",
    public val spanIdHeader: String = "X-JourneyScope-Span-Id",
    public val requireHttps: Boolean = true,
) {
    init {
        require(trustedHosts.isNotEmpty()) { "trustedHosts must not be empty" }
    }

    internal fun trusts(
        scheme: String,
        host: String,
    ): Boolean {
        if (requireHttps && !scheme.equals("https", ignoreCase = true)) return false
        val candidate = host.lowercase()
        return trustedHosts.any { rule ->
            val lowered = rule.lowercase()
            if (lowered.startsWith("*.")) candidate.endsWith(lowered.drop(1)) else candidate == lowered
        }
    }
}

/** Body snippets are only handed to a [BusinessResponseValidator]; they are never stored on spans. */
public class BodyCapture(
    public val maxBytes: Int = DEFAULT_MAX_BYTES,
    public val contentTypes: Set<String> = setOf("application/json", "text/plain"),
) {
    init {
        require(maxBytes in 1..HARD_LIMIT_BYTES) { "maxBytes must be within 1..$HARD_LIMIT_BYTES" }
    }

    /** Compares the media type only, ignoring parameters and case. */

    public fun accepts(contentType: String?): Boolean {
        val base = contentType?.substringBefore(';')?.trim()?.lowercase() ?: return false
        return contentTypes.any { it.lowercase() == base }
    }

    public companion object {
        public const val DEFAULT_MAX_BYTES: Int = 2048
        public const val HARD_LIMIT_BYTES: Int = 4096
    }
}

/** Options for network tracing; by default nothing beyond method, host and sanitized path is recorded. */

public class NetworkTraceConfig(
    public val noJourneyPolicy: NoJourneyPolicy = NoJourneyPolicy.Ignore,
    /** Header names whose values may be recorded; credentials stay denied even when listed. */
    public val headerAllowlist: Set<String> = emptySet(),
    /** Query parameter names whose values may be recorded; every other value is redacted. */
    public val queryParamAllowlist: Set<String> = emptySet(),
    public val captureSizes: Boolean = true,
    public val propagation: PropagationConfig? = null,
    public val businessValidator: BusinessResponseValidator? = null,
    public val bodyCapture: BodyCapture? = null,
)
