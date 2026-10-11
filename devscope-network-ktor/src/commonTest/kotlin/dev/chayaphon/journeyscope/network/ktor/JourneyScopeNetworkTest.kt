package dev.chayaphon.journeyscope.network.ktor

import dev.chayaphon.journeyscope.core.Journey
import dev.chayaphon.journeyscope.core.JourneyScopeConfig
import dev.chayaphon.journeyscope.core.JourneyTracer
import dev.chayaphon.journeyscope.core.launchInJourney
import dev.chayaphon.journeyscope.core.model.JourneySpan
import dev.chayaphon.journeyscope.core.model.MetadataValue
import dev.chayaphon.journeyscope.core.model.SpanCategory
import dev.chayaphon.journeyscope.core.model.SpanStatus
import dev.chayaphon.journeyscope.core.model.TraceStatus
import dev.chayaphon.journeyscope.core.network.BodyCapture
import dev.chayaphon.journeyscope.core.network.BusinessResult
import dev.chayaphon.journeyscope.core.network.BusinessStatus
import dev.chayaphon.journeyscope.core.network.NetworkTraceConfig
import dev.chayaphon.journeyscope.core.network.NoJourneyPolicy
import dev.chayaphon.journeyscope.core.network.PropagationConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JourneyScopeNetworkTest {
    private val tracer = JourneyTracer(JourneyScopeConfig(enabled = true))

    private fun client(
        trace: NetworkTraceConfig = NetworkTraceConfig(),
        timeout: Boolean = false,
        handler: MockRequestHandler,
    ): HttpClient =
        HttpClient(MockEngine(handler)) {
            if (timeout) install(HttpTimeout) { requestTimeoutMillis = 50 }
            install(JourneyScopeNetwork) {
                this.trace = trace
                this.tracer = { this@JourneyScopeNetworkTest.tracer }
            }
        }

    private fun Journey.networkSpans(): List<JourneySpan> = checkNotNull(snapshot()).spans.filter { it.category == SpanCategory.NETWORK }

    private fun JourneySpan.text(key: String) = (metadata[key] as? MetadataValue.Text)?.value

    @Test
    fun successProducesOneNetworkSpanInTheActiveJourney() =
        runTest {
            val journey = tracer.startJourney("Add to Cart")
            val client = client { respond("ok", HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, "2")) }

            val body = withContext(journey.context()) { client.get("https://api.shop.com/cart?token=secret").bodyAsText() }

            assertEquals("ok", body)
            val span = journey.networkSpans().single()
            assertEquals("GET api.shop.com/cart", span.name)
            assertEquals(SpanStatus.OK, span.status)
            assertEquals(MetadataValue.Integer(200), span.metadata["http.status"])
            assertEquals("token", span.text("http.query.keys"))
            assertFalse(
                span.metadata.entries.values
                    .any { (it as? MetadataValue.Text)?.value?.contains("secret") == true },
            )
        }

    @Test
    fun httpErrorsAreClassifiedWithoutThrowing() =
        runTest {
            val journey = tracer.startJourney("j")
            val client =
                client { request ->
                    if (request.url.encodedPath == "/missing") respond("", HttpStatusCode.NotFound) else respond("", HttpStatusCode.BadGateway)
                }

            withContext(journey.context()) {
                assertEquals(404, client.get("https://api.shop.com/missing").status.value)
                assertEquals(502, client.get("https://api.shop.com/down").status.value)
            }

            val spans = journey.networkSpans()
            assertEquals(listOf("HTTP_CLIENT_ERROR", "HTTP_SERVER_ERROR"), spans.map { it.text("http.failure") })
            assertTrue(spans.all { it.status == SpanStatus.FAILED })
        }

    @Test
    fun businessFailureOnHttp200IsReportedSeparately() =
        runTest {
            val json = """{"returnCode":"1001"}"""
            val trace =
                NetworkTraceConfig(
                    bodyCapture = BodyCapture(),
                    businessValidator = { _, response ->
                        val code = Regex("\"returnCode\":\"(\\d+)\"").find(response.bodySnippet.orEmpty())?.groupValues?.get(1)
                        when (code) {
                            null -> null
                            "0000" -> BusinessResult(BusinessStatus.SUCCESS, code)
                            "1001" -> BusinessResult(BusinessStatus.SESSION_EXPIRED, code)
                            else -> BusinessResult(BusinessStatus.FAILURE, code)
                        }
                    },
                )
            val journey = tracer.startJourney("j")
            val client =
                client(trace) {
                    respond(
                        json,
                        HttpStatusCode.OK,
                        headersOf(
                            HttpHeaders.ContentType to listOf("application/json"),
                            HttpHeaders.ContentLength to listOf(json.length.toString()),
                        ),
                    )
                }

            val callerBody = withContext(journey.context()) { client.get("https://api.shop.com/session").bodyAsText() }

            val span = journey.networkSpans().single()
            assertEquals(json, callerBody)
            assertEquals(SpanStatus.OK, span.status)
            assertEquals("SESSION_EXPIRED", span.text("business.status"))
            assertEquals("1001", span.text("returnCode"))
        }

    @Test
    fun streamingResponseIsNotConsumedAndRemainsReadable() =
        runTest {
            val trace = NetworkTraceConfig(bodyCapture = BodyCapture(), businessValidator = { _, _ -> error("must not run on a snippet") })
            val journey = tracer.startJourney("j")
            val client =
                client(trace) {
                    respond(ByteReadChannel("chunk-1chunk-2"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))
                }

            val text = withContext(journey.context()) { client.get("https://api.shop.com/stream").bodyAsText() }

            assertEquals("chunk-1chunk-2", text)
            val span = journey.networkSpans().single()
            assertEquals(MetadataValue.Flag(true), span.metadata["http.streaming"])
            assertNull(span.metadata["business.status"])
        }

    @Test
    fun headersAreAllowlistOnlyAndCredentialsNeverRecorded() =
        runTest {
            val trace = NetworkTraceConfig(headerAllowlist = setOf("x-request-id", "authorization"))
            val journey = tracer.startJourney("j")
            val client = client(trace) { respond("", HttpStatusCode.OK, headersOf("Set-Cookie", "sid=1")) }

            withContext(journey.context()) {
                client.get("https://api.shop.com/a") {
                    header("Authorization", "Bearer top-secret")
                    header("Cookie", "sid=9")
                    header("X-Request-Id", "r-1")
                }
            }

            val keys =
                journey
                    .networkSpans()
                    .single()
                    .metadata.entries
            assertEquals(MetadataValue.Text("r-1"), keys["http.request.header.x-request-id"])
            assertTrue(keys.keys.none { it.contains("authorization") || it.contains("cookie") })
            assertTrue(keys.values.none { (it as? MetadataValue.Text)?.value?.contains("top-secret") == true })
        }

    @Test
    fun timeoutFailsTheSpanAsTimeout() =
        runTest {
            val journey = tracer.startJourney("j")
            val client =
                client(timeout = true) {
                    delay(5_000)
                    respond("", HttpStatusCode.OK)
                }

            withContext(Dispatchers.Default + journey.context()) {
                assertFailsWith<HttpRequestTimeoutException> { client.get("https://api.shop.com/slow") }
            }

            val span = journey.networkSpans().single()
            assertEquals(SpanStatus.FAILED, span.status)
            assertEquals("TIMEOUT", span.text("http.failure"))
            assertFalse(span.isOpen)
        }

    @Test
    fun cancellationClosesTheSpanAsCancelledAndPropagates() =
        runTest {
            val journey = tracer.startJourney("j")
            val started = CompletableDeferred<Unit>()
            val client =
                client {
                    started.complete(Unit)
                    awaitCancellation()
                }

            val job: Job = CoroutineScope(Dispatchers.Default).launchInJourney(journey) { client.get("https://api.shop.com/hang") }
            started.await()
            job.cancel()
            job.join()

            assertTrue(job.isCancelled)
            val span = journey.networkSpans().single()
            assertEquals(SpanStatus.CANCELLED, span.status)
            assertEquals("CANCELLED", span.text("http.failure"))
        }

    @Test
    fun transportExceptionIsRethrownUnchangedAndSpanFails() =
        runTest {
            val journey = tracer.startJourney("j")
            val boom = IllegalStateException("socket reset")
            val client = client { throw boom }

            val thrown = withContext(journey.context()) { assertFailsWith<IllegalStateException> { client.get("https://api.shop.com/x") } }

            assertEquals(boom.message, thrown.message)
            assertEquals(SpanStatus.FAILED, journey.networkSpans().single().status)
        }

    @Test
    fun concurrentRequestsStayInTheirOwnJourneys() =
        runTest {
            val first = tracer.startJourney("first")
            val second = tracer.startJourney("second")
            val client =
                client { request ->
                    delay(10)
                    respond(request.url.encodedPath, HttpStatusCode.OK)
                }

            listOf(first, second)
                .map { journey ->
                    async { withContext(journey.context()) { repeat(3) { client.get("https://api.shop.com/${journey.name}") } } }
                }.awaitAll()

            assertEquals(listOf("GET api.shop.com/first"), first.networkSpans().map { it.name }.distinct())
            assertEquals(3, first.networkSpans().size)
            assertEquals(listOf("GET api.shop.com/second"), second.networkSpans().map { it.name }.distinct())
            assertEquals(3, second.networkSpans().size)
        }

    @Test
    fun requestOutsideAJourneyFollowsThePolicy() =
        runTest {
            val unrelated = tracer.startJourney("most recent ui journey")

            client { respond("", HttpStatusCode.OK) }.get("https://api.shop.com/background")
            assertTrue(unrelated.networkSpans().isEmpty())
            assertEquals(1, tracer.recorder.snapshot().size)

            client(NetworkTraceConfig(noJourneyPolicy = NoJourneyPolicy.Standalone)) { respond("", HttpStatusCode.OK) }
                .get("https://api.shop.com/background")

            val standalone = tracer.recorder.snapshot().single { it.id != unrelated.id }
            assertEquals(TraceStatus.COMPLETED, standalone.status)
            assertTrue(unrelated.networkSpans().isEmpty())
        }

    @Test
    fun propagationHeaderIsSentOnlyToTrustedHosts() =
        runTest {
            val seen = mutableMapOf<String, String?>()
            val trace = NetworkTraceConfig(propagation = PropagationConfig(setOf("api.shop.com")))
            val journey = tracer.startJourney("j")
            val client =
                client(trace) { request ->
                    seen[request.url.host] = request.headers["X-JourneyScope-Trace-Id"]
                    respond("", HttpStatusCode.OK)
                }

            withContext(journey.context()) {
                client.post("https://api.shop.com/a")
                client.post("https://cdn.other.com/b")
            }

            assertEquals(journey.id.value, seen["api.shop.com"])
            assertNull(seen["cdn.other.com"])
        }
}
