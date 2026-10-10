package dev.journeyscope.core.network

import dev.journeyscope.core.RedactionPolicy

internal object HeaderSanitizer {
    private const val MAX_VALUE_LENGTH = 128

    private val alwaysDenied =
        setOf(
            "authorization",
            "proxy-authorization",
            "cookie",
            "set-cookie",
            "x-api-key",
            "x-auth-token",
            "x-csrf-token",
            "x-xsrf-token",
        )

    /** Allowlisted headers only; a credential header is dropped even if the host listed it. */
    fun collect(
        prefix: String,
        headers: Map<String, List<String>>,
        allowlist: Set<String>,
        policy: RedactionPolicy,
    ): Map<String, String> {
        if (allowlist.isEmpty() || headers.isEmpty()) return emptyMap()
        val allowed = allowlist.map { it.lowercase() }.toSet()
        val result = LinkedHashMap<String, String>()
        for ((name, values) in headers) {
            val key = name.lowercase()
            if (key !in allowed || key in alwaysDenied || policy.isSensitive(key)) continue
            result["$prefix$key"] = values.joinToString(",").take(MAX_VALUE_LENGTH)
        }
        return result
    }
}
