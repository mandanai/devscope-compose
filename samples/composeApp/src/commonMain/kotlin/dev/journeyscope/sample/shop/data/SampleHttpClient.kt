package dev.journeyscope.sample.shop.data

import dev.journeyscope.core.JourneyScope
import dev.journeyscope.core.JourneyTracer
import dev.journeyscope.core.network.BodyCapture
import dev.journeyscope.core.network.BusinessResponseValidator
import dev.journeyscope.core.network.BusinessResult
import dev.journeyscope.core.network.BusinessStatus
import dev.journeyscope.core.network.NetworkTraceConfig
import dev.journeyscope.network.ktor.JourneyScopeNetwork
import dev.journeyscope.sample.shop.LatencyProfile
import dev.journeyscope.sample.shop.SampleScenario
import dev.journeyscope.sample.shop.SimulatedNetworkException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay

/** Maps the API's `returnCode` to a business outcome, independent of the HTTP status. */
object CartBusinessValidator : BusinessResponseValidator {
    override fun inspect(
        request: dev.journeyscope.core.network.NetworkRequestInfo,
        response: dev.journeyscope.core.network.NetworkResponseInfo,
    ): BusinessResult? {
        val code = jsonString(response.bodySnippet ?: return null, "returnCode") ?: return null
        return BusinessResult(if (code == KtorCartApi.SUCCESS_CODE) BusinessStatus.SUCCESS else BusinessStatus.FAILURE, code)
    }
}

class SimulatedLatencyConfig {
    var latency: LatencyProfile = LatencyProfile.Default
}

/**
 * MockEngine cannot share a test scheduler, so latency is applied in the client pipeline on the caller's
 * coroutine; installed after the tracing plugin it falls inside the NETWORK span.
 */
val SimulatedLatency: ClientPlugin<SimulatedLatencyConfig> = createClientPlugin("SimulatedLatency", ::SimulatedLatencyConfig) {
    on(Send) { request ->
        val scenario = SampleScenario.entries.firstOrNull { it.name == request.headers[KtorCartApi.SCENARIO_HEADER] } ?: SampleScenario.Normal
        delay(pluginConfig.latency.networkFor(scenario))
        proceed(request)
    }
}

fun sampleHttpClient(
    latency: LatencyProfile = LatencyProfile.Default,
    tracer: () -> JourneyTracer = { JourneyScope.tracer },
    engineDispatcher: CoroutineDispatcher? = null,
): HttpClient = HttpClient(sampleCartEngine(engineDispatcher)) {
    install(JourneyScopeNetwork) {
        this.tracer = tracer
        trace = NetworkTraceConfig(bodyCapture = BodyCapture(), businessValidator = CartBusinessValidator)
    }
    install(SimulatedLatency) { this.latency = latency }
}

private fun sampleCartEngine(dispatcher: CoroutineDispatcher?): MockEngine {
    val config = MockEngineConfig()
    dispatcher?.let { config.dispatcher = it }
    config.addHandler { request ->
        when (request.headers[KtorCartApi.SCENARIO_HEADER]) {
            SampleScenario.NetworkException.name -> throw SimulatedNetworkException()
            SampleScenario.BusinessFailure.name -> json("""{"returnCode":"2001","reason":"OUT_OF_STOCK"}""")
            else -> {
                val quantity = Regex("\"quantity\":(\\d+)").find((request.body as? TextContent)?.text.orEmpty())?.groupValues?.get(1) ?: "1"
                json("""{"returnCode":"${KtorCartApi.SUCCESS_CODE}","quantity":$quantity}""")
            }
        }
    }
    return MockEngine(config)
}

private fun MockRequestHandleScope.json(body: String): HttpResponseData = respond(
    content = body,
    status = HttpStatusCode.OK,
    headers = headersOf(
        HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
        HttpHeaders.ContentLength to listOf(body.length.toString()),
    ),
)
