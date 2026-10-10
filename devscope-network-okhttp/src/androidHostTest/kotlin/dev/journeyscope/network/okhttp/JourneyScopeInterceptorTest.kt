package dev.journeyscope.network.okhttp

import dev.journeyscope.core.Journey
import dev.journeyscope.core.JourneyScopeConfig
import dev.journeyscope.core.JourneyTracer
import dev.journeyscope.core.TraceContext
import dev.journeyscope.core.model.JourneySpan
import dev.journeyscope.core.model.MetadataValue
import dev.journeyscope.core.model.SpanCategory
import dev.journeyscope.core.model.SpanStatus
import dev.journeyscope.core.network.BodyCapture
import dev.journeyscope.core.network.BusinessResult
import dev.journeyscope.core.network.BusinessStatus
import dev.journeyscope.core.network.NetworkTraceConfig
import dev.journeyscope.core.network.NetworkTracing
import dev.journeyscope.core.network.NoJourneyPolicy
import dev.journeyscope.core.network.PropagationConfig
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class JourneyScopeInterceptorTest {
    private val tracer = JourneyTracer(JourneyScopeConfig(enabled = true))
    private val server = MockWebServer()

    @BeforeTest
    fun start() = server.start()

    @AfterTest
    fun stop() = server.close()

    private fun client(trace: NetworkTraceConfig = NetworkTraceConfig(), configure: OkHttpClient.Builder.() -> Unit = {}): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(JourneyScopeInterceptor(NetworkTracing(trace) { tracer }))
            .apply(configure)
            .build()

    private fun Journey.requestFor(path: String, headers: Map<String, String> = emptyMap()): Request =
        Request.Builder().url(server.url(path)).apply { headers.forEach { (k, v) -> header(k, v) } }
            .journeyContext(context()[TraceContext]).build()

    private fun Journey.networkSpans(): List<JourneySpan> = checkNotNull(snapshot()).spans.filter { it.category == SpanCategory.NETWORK }

    private fun JourneySpan.text(key: String) = (metadata[key] as? MetadataValue.Text)?.value

    @Test
    fun successfulCallRecordsSpanAndLeavesBodyUntouched() {
        server.enqueue(MockResponse.Builder().body("hello").build())
        val journey = tracer.startJourney("j")

        val body = client().newCall(journey.requestFor("/cart?token=secret")).execute().use { it.body.string() }

        assertEquals("hello", body)
        val span = journey.networkSpans().single()
        assertEquals(SpanStatus.OK, span.status)
        assertEquals(MetadataValue.Integer(200), span.metadata["http.status"])
        assertEquals("token", span.text("http.query.keys"))
        assertFalse(span.metadata.entries.values.any { (it as? MetadataValue.Text)?.value?.contains("secret") == true })
    }

    @Test
    fun httpErrorsAreClassified() {
        server.enqueue(MockResponse.Builder().code(404).build())
        server.enqueue(MockResponse.Builder().code(503).build())
        val journey = tracer.startJourney("j")
        val client = client()

        client.newCall(journey.requestFor("/a")).execute().close()
        client.newCall(journey.requestFor("/b")).execute().close()

        assertEquals(listOf("HTTP_CLIENT_ERROR", "HTTP_SERVER_ERROR"), journey.networkSpans().map { it.text("http.failure") })
    }

    @Test
    fun businessFailureUsesPeekedBodyAndCallerStillReadsIt() {
        val json = """{"returnCode":"1001"}"""
        val trace = NetworkTraceConfig(
            bodyCapture = BodyCapture(),
            businessValidator = { _, response ->
                if (response.bodySnippet?.contains("\"1001\"") == true) BusinessResult(BusinessStatus.SESSION_EXPIRED, "1001") else null
            },
        )
        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "application/json").body(json).build())
        val journey = tracer.startJourney("j")

        val body = client(trace).newCall(journey.requestFor("/s")).execute().use { it.body.string() }

        assertEquals(json, body)
        val span = journey.networkSpans().single()
        assertEquals(SpanStatus.OK, span.status)
        assertEquals("SESSION_EXPIRED", span.text("business.status"))
    }

    @Test
    fun streamingBodyIsNotBufferedByTheInterceptor() {
        val trace = NetworkTraceConfig(bodyCapture = BodyCapture(), businessValidator = { _, _ -> error("must not run on a snippet") })
        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "text/event-stream").body("data: 1\n\n").build())
        val journey = tracer.startJourney("j")

        val body = client(trace).newCall(journey.requestFor("/events")).execute().use { it.body.string() }

        assertEquals("data: 1\n\n", body)
        assertEquals(MetadataValue.Flag(true), journey.networkSpans().single().metadata["http.streaming"])
    }

    @Test
    fun timeoutIsRethrownAsTheOriginalExceptionAndClassified() {
        server.enqueue(MockResponse.Builder().headersDelay(2, TimeUnit.SECONDS).build())
        val journey = tracer.startJourney("j")
        val client = client { readTimeout(100, TimeUnit.MILLISECONDS) }

        assertFailsWith<SocketTimeoutException> { client.newCall(journey.requestFor("/slow")).execute() }

        val span = journey.networkSpans().single()
        assertEquals(SpanStatus.FAILED, span.status)
        assertEquals("TIMEOUT", span.text("http.failure"))
    }

    @Test
    fun cancelledCallClosesTheSpanAsCancelled() {
        server.enqueue(MockResponse.Builder().headersDelay(2, TimeUnit.SECONDS).build())
        val journey = tracer.startJourney("j")
        val call = client().newCall(journey.requestFor("/hang"))

        Thread { Thread.sleep(150); call.cancel() }.start()
        assertFailsWith<IOException> { call.execute() }

        val span = journey.networkSpans().single()
        assertEquals(SpanStatus.CANCELLED, span.status)
        assertEquals("CANCELLED", span.text("http.failure"))
    }

    @Test
    fun interceptorExceptionsFromLaterInterceptorsKeepTheirIdentity() {
        val boom = IllegalStateException("downstream")
        val journey = tracer.startJourney("j")
        val client = client { addInterceptor(Interceptor { throw boom }) }

        val thrown = assertFailsWith<IllegalStateException> { client.newCall(journey.requestFor("/x")).execute() }

        assertSame(boom, thrown)
        assertEquals(SpanStatus.FAILED, journey.networkSpans().single().status)
    }

    @Test
    fun requestWithoutTagFollowsNoJourneyPolicy() {
        server.enqueue(MockResponse.Builder().build())
        server.enqueue(MockResponse.Builder().build())
        val unrelated = tracer.startJourney("most recent")
        val untagged = Request.Builder().url(server.url("/bg")).build()

        client().newCall(untagged).execute().close()
        assertTrue(unrelated.networkSpans().isEmpty())
        assertEquals(1, tracer.recorder.snapshot().size)

        client(NetworkTraceConfig(noJourneyPolicy = NoJourneyPolicy.Standalone)).newCall(untagged).execute().close()
        assertEquals(2, tracer.recorder.snapshot().size)
        assertTrue(unrelated.networkSpans().isEmpty())
    }

    @Test
    fun concurrentCallsKeepTheirOwnJourneys() = runTest {
        repeat(6) { server.enqueue(MockResponse.Builder().build()) }
        val a = tracer.startJourney("a")
        val b = tracer.startJourney("b")
        val client = client()

        val threads = listOf(a, b).map { journey ->
            Thread { repeat(3) { client.newCall(journey.requestFor("/${journey.name}")).execute().close() } }.apply { start() }
        }
        threads.forEach { it.join() }

        assertEquals(setOf("GET ${server.hostName}/a"), a.networkSpans().map { it.name }.toSet())
        assertEquals(3, a.networkSpans().size)
        assertEquals(3, b.networkSpans().size)
    }

    @Test
    fun propagationHeaderReachesOnlyTrustedHostAndOriginalRequestIsUnchanged() {
        server.enqueue(MockResponse.Builder().build())
        val journey = tracer.startJourney("j")
        val trace = NetworkTraceConfig(propagation = PropagationConfig(setOf(server.hostName), requireHttps = false))

        client(trace).newCall(journey.requestFor("/p")).execute().close()

        assertEquals(journey.id.value, server.takeRequest().headers["X-JourneyScope-Trace-Id"])
    }

    @Test
    fun noPropagationHeaderByDefault() {
        server.enqueue(MockResponse.Builder().build())
        val journey = tracer.startJourney("j")

        client().newCall(journey.requestFor("/p")).execute().close()

        assertEquals(null, server.takeRequest().headers["X-JourneyScope-Trace-Id"])
    }
}
