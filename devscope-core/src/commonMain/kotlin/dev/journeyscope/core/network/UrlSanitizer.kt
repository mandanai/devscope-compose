package dev.journeyscope.core.network

import dev.journeyscope.core.RedactionPolicy

internal class SanitizedUrl(
    val scheme: String,
    val host: String,
    val port: Int?,
    val path: String,
    val queryKeys: List<String>,
    val allowedQueryValues: Map<String, String>,
    val signed: Boolean,
)

internal object UrlSanitizer {
    private const val REDACTED_SEGMENT = "{id}"
    private const val OPAQUE_SEGMENT_LENGTH = 24
    private const val MIN_HEX_LENGTH = 16
    private const val MAX_PLAIN_NUMBER_LENGTH = 9
    private const val MAX_PATH_LENGTH = 200

    private val signedMarkers = setOf(
        "x-amz-signature", "x-amz-credential", "x-amz-security-token", "x-goog-signature", "x-goog-credential",
        "signature", "sig", "googleaccessid", "sas", "sv", "se", "skoid",
    )

    fun sanitize(url: String, allowedQueryParams: Set<String>, policy: RedactionPolicy): SanitizedUrl {
        val noFragment = url.substringBefore('#')
        val schemeEnd = noFragment.indexOf("://")
        val scheme = if (schemeEnd > 0) noFragment.substring(0, schemeEnd).lowercase() else ""
        val afterScheme = if (schemeEnd > 0) noFragment.substring(schemeEnd + 3) else noFragment
        val authorityEnd = afterScheme.indexOfFirst { it == '/' || it == '?' }.let { if (it < 0) afterScheme.length else it }
        val authority = afterScheme.substring(0, authorityEnd).substringAfterLast('@').lowercase()
        val portSeparator = authority.lastIndexOf(':').takeIf { it > authority.lastIndexOf(']') }
        val host = if (portSeparator != null) authority.substring(0, portSeparator) else authority
        val port = portSeparator?.let { authority.substring(it + 1).toIntOrNull() }
        val rest = afterScheme.substring(authorityEnd)
        val rawPath = rest.substringBefore('?')
        val rawQuery = if (rest.contains('?')) rest.substringAfter('?') else ""

        val params = rawQuery.split('&').filter { it.isNotEmpty() }.map { it.substringBefore('=') to it.substringAfter('=', "") }
        val signed = params.any { (key, _) -> key.lowercase() in signedMarkers }
        val allowed = allowedQueryParams.map { it.lowercase() }.toSet()
        val values = if (signed) {
            emptyMap()
        } else {
            params.filter { (key, _) -> key.lowercase() in allowed && !policy.isSensitive(key) }.toMap()
        }
        return SanitizedUrl(
            scheme = scheme,
            host = host,
            port = port,
            path = sanitizePath(rawPath),
            queryKeys = if (signed) emptyList() else params.map { it.first }.distinct(),
            allowedQueryValues = values,
            signed = signed,
        )
    }

    private fun sanitizePath(path: String): String {
        if (path.isEmpty()) return "/"
        return path.split('/').joinToString("/") { if (isOpaque(it)) REDACTED_SEGMENT else it }.take(MAX_PATH_LENGTH)
    }

    private fun isOpaque(segment: String): Boolean = when {
        segment.length > MAX_PLAIN_NUMBER_LENGTH && segment.all { it.isDigit() } -> true
        segment.length >= MIN_HEX_LENGTH && segment.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == '-' } -> true
        segment.length >= OPAQUE_SEGMENT_LENGTH && segment.any { it.isDigit() } && segment.any { it.isLetter() } &&
            segment.all { it.isLetterOrDigit() || it == '_' || it == '-' } -> true
        else -> false
    }
}
