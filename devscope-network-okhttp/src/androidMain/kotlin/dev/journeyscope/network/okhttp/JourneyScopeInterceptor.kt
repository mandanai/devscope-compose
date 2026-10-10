package dev.journeyscope.network.okhttp

import dev.journeyscope.core.TraceContext
import dev.journeyscope.core.currentTraceContext
import dev.journeyscope.core.network.NetworkRequestInfo
import dev.journeyscope.core.network.NetworkResponseInfo
import dev.journeyscope.core.network.NetworkTraceConfig
import dev.journeyscope.core.network.NetworkTracing
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * Records one NETWORK span per request. OkHttp runs interceptors on its own threads, so the journey
 * is taken from a tag on the request (see [journeyContext] / [tagWithCurrentJourney]); an untagged
 * request follows [NetworkTraceConfig.noJourneyPolicy]. Install as an application interceptor for one
 * span per call, or as a network interceptor for one span per redirect or retry hop.
 */
public class JourneyScopeInterceptor(
    private val tracing: NetworkTracing = NetworkTracing(),
) : Interceptor {
    /** Uses [config] with the default tracer. */
    public constructor(config: NetworkTraceConfig) : this(NetworkTracing(config))

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val call = tracing.begin(original.toInfo(), original.tag(TraceContext::class.java))
        val outgoing =
            if (call.propagationHeaders.isEmpty()) {
                original
            } else {
                original.newBuilder().apply { call.propagationHeaders.forEach { (name, value) -> header(name, value) } }.build()
            }
        val response =
            try {
                chain.proceed(outgoing)
            } catch (t: Throwable) {
                call.fail(t, cancelled = chain.call().isCanceled())
                throw t
            }
        call.complete(response.toInfo(tracing.config))
        return response
    }
}

/** For callers that already hold a [TraceContext], e.g. from `journey.context()[TraceContext]`. */
public fun Request.Builder.journeyContext(context: TraceContext?): Request.Builder =
    apply {
        if (context != null) tag(TraceContext::class.java, context)
    }

/** Binds the request to the journey of the calling coroutine, if any. */
public suspend fun Request.Builder.tagWithCurrentJourney(): Request.Builder = journeyContext(currentTraceContext())

private fun Request.toInfo(): NetworkRequestInfo =
    NetworkRequestInfo(
        method = method,
        url = url.toString(),
        headers = headers.toMultimap(),
        contentLength =
            try {
                body?.contentLength()?.takeIf { it >= 0 }
            } catch (ignored: IOException) {
                null
            },
    )

private fun Response.toInfo(config: NetworkTraceConfig): NetworkResponseInfo {
    val length = body.contentLength().takeIf { it >= 0 }
    val type = header("Content-Type")
    val streaming = length == null || type?.startsWith("text/event-stream", ignoreCase = true) == true
    val capture = config.bodyCapture
    val snippet =
        if (
            capture != null && config.businessValidator != null && !streaming &&
            length <= capture.maxBytes && capture.accepts(type)
        ) {
            peekBody(capture.maxBytes.toLong()).string()
        } else {
            null
        }
    return NetworkResponseInfo(
        statusCode = code,
        headers = headers.toMultimap(),
        contentLength = length,
        protocol = protocol.toString(),
        streaming = streaming,
        bodySnippet = snippet,
    )
}
