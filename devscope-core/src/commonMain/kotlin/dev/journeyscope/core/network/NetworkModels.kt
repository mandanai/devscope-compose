package dev.journeyscope.core.network

import kotlin.jvm.JvmInline

/** Client-neutral description of an outgoing request; adapters translate their own types into this. */
public class NetworkRequestInfo(
    public val method: String,
    public val url: String,
    public val headers: Map<String, List<String>> = emptyMap(),
    public val contentLength: Long? = null,
)

public class NetworkResponseInfo(
    public val statusCode: Int,
    public val headers: Map<String, List<String>> = emptyMap(),
    public val contentLength: Long? = null,
    public val protocol: String? = null,
    /** True when the body is not buffered and was left untouched. */
    public val streaming: Boolean = false,
    /** Present only when body capture is enabled and the body fit the configured limit. */
    public val bodySnippet: String? = null,
)

public enum class NetworkFailure {
    TIMEOUT,
    CONNECTION,
    DNS,
    TLS,
    CANCELLED,
    HTTP_CLIENT_ERROR,
    HTTP_SERVER_ERROR,
    PROTOCOL,
    UNKNOWN,
}

@JvmInline
public value class BusinessStatus(public val name: String) {
    override fun toString(): String = name

    public companion object {
        public val SUCCESS: BusinessStatus = BusinessStatus("SUCCESS")
        public val FAILURE: BusinessStatus = BusinessStatus("FAILURE")
        public val SESSION_EXPIRED: BusinessStatus = BusinessStatus("SESSION_EXPIRED")
    }
}

public data class BusinessResult(val status: BusinessStatus, val code: String? = null)

/**
 * Application-level success classification, independent of the HTTP status.
 * Must be registered explicitly; return null when the response is not in scope.
 */
public fun interface BusinessResponseValidator {
    public fun inspect(request: NetworkRequestInfo, response: NetworkResponseInfo): BusinessResult?
}
