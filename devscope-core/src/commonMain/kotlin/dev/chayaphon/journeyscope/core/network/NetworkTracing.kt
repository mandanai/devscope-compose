package dev.chayaphon.journeyscope.core.network

import dev.chayaphon.journeyscope.core.Journey
import dev.chayaphon.journeyscope.core.JourneyScope
import dev.chayaphon.journeyscope.core.JourneyTracer
import dev.chayaphon.journeyscope.core.SpanHandle
import dev.chayaphon.journeyscope.core.TraceContext
import dev.chayaphon.journeyscope.core.model.DiagnosticMetadata
import dev.chayaphon.journeyscope.core.model.SpanCategory
import dev.chayaphon.journeyscope.core.model.SpanId
import dev.chayaphon.journeyscope.core.model.SpanStatus
import dev.chayaphon.journeyscope.core.model.diagnosticMetadata
import dev.chayaphon.journeyscope.core.safely

/**
 * Client-independent engine behind the Ktor and OkHttp adapters: one NETWORK span per request,
 * created only inside a journey or as dictated by [NetworkTraceConfig.noJourneyPolicy].
 */
public class NetworkTracing(
    public val config: NetworkTraceConfig = NetworkTraceConfig(),
    private val tracer: () -> JourneyTracer = { JourneyScope.tracer },
) {
    /** [context] must come from the calling coroutine (or an explicit tag); it is never guessed. */
    public fun begin(
        request: NetworkRequestInfo,
        context: TraceContext?,
    ): NetworkCall =
        try {
            open(request, context)
        } catch (ignored: Throwable) {
            // SDK faults must never surface in application networking.
            NetworkCall.Inert
        }

    private fun open(
        request: NetworkRequestInfo,
        context: TraceContext?,
    ): NetworkCall {
        val redaction = tracer().config.redactionPolicy
        val url = UrlSanitizer.sanitize(request.url, config.queryParamAllowlist, redaction)
        val method = request.method.uppercase()
        val name = "$method ${url.host}${url.path}"

        var ownsJourney = false
        val journey: Journey
        val parent: SpanId?
        if (context != null) {
            if (!context.journey.isRecording) return NetworkCall.Inert
            journey = context.journey
            parent = context.spanId
        } else {
            when (val policy = config.noJourneyPolicy) {
                NoJourneyPolicy.Ignore -> {
                    return NetworkCall.Inert
                }

                NoJourneyPolicy.Standalone -> {
                    journey = tracer().startJourney(name, diagnosticMetadata { put("journey.source", "network") })
                    ownsJourney = true
                }

                is NoJourneyPolicy.Explicit -> {
                    journey = policy.journey
                }
            }
            parent = null
            if (!journey.isRecording) return NetworkCall.Inert
        }

        val metadata =
            diagnosticMetadata {
                put("http.method", method)
                put("http.host", url.host)
                url.port?.let { put("http.port", it) }
                put("http.path", url.path)
                put("http.scheme", url.scheme)
                put("detail", "$method ${url.path}")
                if (url.signed) put("http.url.signed", true)
                if (url.queryKeys.isNotEmpty()) put("http.query.keys", url.queryKeys.joinToString(","))
                url.allowedQueryValues.forEach { (key, value) -> put("http.query.$key", value) }
                HeaderSanitizer
                    .collect("http.request.header.", request.headers, config.headerAllowlist, redaction)
                    .forEach { (key, value) -> put(key, value) }
                if (config.captureSizes) request.contentLength?.takeIf { it >= 0 }?.let { put("http.request.bytes", it) }
            }
        val span = journey.openSpanForNetwork(name, metadata, parent)
        if (!span.isRecording) {
            if (ownsJourney) journey.cancel("span not admitted")
            return NetworkCall.Inert
        }
        return NetworkCall(
            span,
            journey,
            ownsJourney,
            request,
            config,
            redaction,
            "$method ${url.path}",
            propagationFor(url, journey, span),
        )
    }

    private fun propagationFor(
        url: SanitizedUrl,
        journey: Journey,
        span: SpanHandle,
    ): Map<String, String> {
        val propagation = config.propagation ?: return emptyMap()
        if (!propagation.trusts(url.scheme, url.host)) return emptyMap()
        return mapOf(propagation.traceIdHeader to journey.id.value, propagation.spanIdHeader to span.id.value)
    }
}

private fun Journey.openSpanForNetwork(
    name: String,
    metadata: DiagnosticMetadata,
    parent: SpanId?,
): SpanHandle = openSpan(name, SpanCategory.NETWORK, metadata, parent)

/** One in-flight request span; call exactly one of [complete], [fail] or [cancel]. */

public class NetworkCall internal constructor(
    private val span: SpanHandle,
    private val journey: Journey,
    private val ownsJourney: Boolean,
    private val request: NetworkRequestInfo?,
    private val config: NetworkTraceConfig,
    private val redaction: dev.chayaphon.journeyscope.core.RedactionPolicy,
    private val label: String,
    /** Empty unless propagation is configured and the destination is trusted. */
    public val propagationHeaders: Map<String, String>,
) {
    public val isRecording: Boolean get() = span.isRecording

    /** Closes the span from response headers; the body is never read here. */
    public fun complete(response: NetworkResponseInfo) {
        safely {
            val failure = FailureClassifier.forStatus(response.statusCode)
            val business = request?.let { safelyInspect(it, response) }
            val metadata =
                diagnosticMetadata {
                    put("http.status", response.statusCode)
                    put("detail", "$label · ${response.statusCode}")
                    response.protocol?.let { put("http.protocol", it) }
                    if (response.streaming) put("http.streaming", true)
                    if (config.captureSizes) response.contentLength?.takeIf { it >= 0 }?.let { put("http.response.bytes", it) }
                    failure?.let { put("http.failure", it.name) }
                    HeaderSanitizer
                        .collect("http.response.header.", response.headers, config.headerAllowlist, redaction)
                        .forEach { (key, value) -> put(key, value) }
                    business?.let {
                        put("business.status", it.status.name)
                        it.code?.let { code -> put("returnCode", code) }
                    }
                }
            if (failure == null) {
                span.end(SpanStatus.OK, metadata)
                finishJourney(null, SpanStatus.OK)
            } else {
                val error = HttpStatusFailure(response.statusCode, failure)
                span.fail(error, metadata)
                finishJourney(error, SpanStatus.FAILED)
            }
        }
    }

    /** Closes the span for a transport failure; set [cancelled] when the client reports its own cancellation. */
    public fun fail(
        throwable: Throwable,
        cancelled: Boolean = false,
    ) {
        safely {
            val kind = FailureClassifier.forThrowable(throwable, cancelled)
            val metadata =
                diagnosticMetadata {
                    put("http.failure", kind.name)
                    put("detail", "$label · ${kind.name}")
                }
            if (kind == NetworkFailure.CANCELLED) {
                span.end(SpanStatus.CANCELLED, metadata)
                finishJourney(null, SpanStatus.CANCELLED)
            } else {
                span.fail(throwable, metadata)
                finishJourney(throwable, SpanStatus.FAILED)
            }
        }
    }

    /** Closes the span as CANCELLED. */

    public fun cancel() {
        safely {
            span.end(SpanStatus.CANCELLED, diagnosticMetadata { put("http.failure", NetworkFailure.CANCELLED.name) })
            finishJourney(null, SpanStatus.CANCELLED)
        }
    }

    private fun safelyInspect(
        request: NetworkRequestInfo,
        response: NetworkResponseInfo,
    ): BusinessResult? =
        try {
            config.businessValidator?.inspect(request, response)
        } catch (ignored: Throwable) {
            // A faulty validator must not change how the transport result is reported.
            null
        }

    private fun finishJourney(
        error: Throwable?,
        status: SpanStatus,
    ) {
        if (!ownsJourney) return
        when (status) {
            SpanStatus.CANCELLED -> journey.cancel()
            SpanStatus.FAILED -> journey.fail(error ?: IllegalStateException("network failure"))
            else -> journey.finish()
        }
    }

    internal companion object {
        val Inert: NetworkCall =
            NetworkCall(
                span = dev.chayaphon.journeyscope.core.noopSpanHandle(),
                journey = dev.chayaphon.journeyscope.core.noopJourney(),
                ownsJourney = false,
                request = null,
                config = NetworkTraceConfig(),
                redaction = dev.chayaphon.journeyscope.core.RedactionPolicy.Default,
                label = "",
                propagationHeaders = emptyMap(),
            )
    }
}

internal class HttpStatusFailure(
    val statusCode: Int,
    val kind: NetworkFailure,
) : Exception("HTTP $statusCode")
