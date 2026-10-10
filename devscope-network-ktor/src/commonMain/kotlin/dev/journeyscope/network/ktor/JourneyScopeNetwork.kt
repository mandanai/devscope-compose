package dev.journeyscope.network.ktor

import dev.journeyscope.core.JourneyScope
import dev.journeyscope.core.JourneyTracer
import dev.journeyscope.core.TraceContext
import dev.journeyscope.core.network.NetworkRequestInfo
import dev.journeyscope.core.network.NetworkResponseInfo
import dev.journeyscope.core.network.NetworkTraceConfig
import dev.journeyscope.core.network.NetworkTracing
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.save
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.util.toMap
import kotlinx.coroutines.currentCoroutineContext

/** Install-time settings for [JourneyScopeNetwork]; [tracer] is evaluated on each request. */
public class JourneyScopeNetworkConfig {
    public var trace: NetworkTraceConfig = NetworkTraceConfig()
    public var tracer: () -> JourneyTracer = { JourneyScope.tracer }
}

/**
 * Records one NETWORK span per request that reaches the engine. The journey is read from the calling
 * coroutine's [TraceContext]; the response body is never consumed unless body capture is enabled.
 * Install after retry/redirect plugins so each attempt and hop gets its own span.
 */
public val JourneyScopeNetwork: ClientPlugin<JourneyScopeNetworkConfig> =
    createClientPlugin("JourneyScopeNetwork", ::JourneyScopeNetworkConfig) {
        val tracing = NetworkTracing(pluginConfig.trace, pluginConfig.tracer)
        val config = pluginConfig.trace

        on(Send) { request ->
            val call = tracing.begin(request.toInfo(), currentCoroutineContext()[TraceContext])
            call.propagationHeaders.forEach { (name, value) -> request.headers[name] = value }
            var origin: HttpClientCall? = null
            try {
                val sent = proceed(request)
                val captured = captureBody(sent, config)
                origin = captured.call
                call.complete(sent.response.toInfo(captured.snippet))
                origin
            } catch (t: Throwable) {
                if (origin == null) call.fail(t)
                throw t
            }
        }
    }

private class Captured(
    val call: HttpClientCall,
    val snippet: String?,
)

private suspend fun captureBody(
    call: HttpClientCall,
    config: NetworkTraceConfig,
): Captured {
    val capture = config.bodyCapture
    if (capture == null || config.businessValidator == null) return Captured(call, null)
    val response = call.response
    val length = response.contentLength()
    val eligible =
        !response.isStreaming() && length != null && length in 0..capture.maxBytes &&
            capture.contentTypesAccept(response.contentType())
    if (!eligible) return Captured(call, null)
    val saved = call.save()
    return Captured(saved, saved.response.bodyAsText().take(capture.maxBytes))
}

private fun dev.journeyscope.core.network.BodyCapture.contentTypesAccept(type: ContentType?): Boolean = accepts(type?.let { "${it.contentType}/${it.contentSubtype}" })

private fun HttpRequestBuilder.toInfo(): NetworkRequestInfo =
    NetworkRequestInfo(
        method = method.value,
        url = url.buildString(),
        headers = headers.build().toMap(),
        contentLength = (body as? OutgoingContent)?.contentLength,
    )

private fun HttpResponse.isStreaming(): Boolean {
    val type = contentType()
    return contentLength() == null || (type?.contentType == "text" && type.contentSubtype == "event-stream")
}

private fun HttpResponse.toInfo(snippet: String?): NetworkResponseInfo =
    NetworkResponseInfo(
        statusCode = status.value,
        headers = headers.toMap(),
        contentLength = contentLength(),
        protocol = version.toString(),
        streaming = isStreaming(),
        bodySnippet = snippet,
    )
