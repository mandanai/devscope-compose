package dev.journeyscope.core.network

import dev.journeyscope.core.TraceContext
import dev.journeyscope.core.model.MetadataValue
import dev.journeyscope.core.model.SpanCategory
import dev.journeyscope.core.model.SpanStatus
import dev.journeyscope.core.model.TraceStatus
import dev.journeyscope.core.span
import dev.journeyscope.core.testTracer
import dev.journeyscope.core.trace
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NetworkTracingTest {
    private val request = NetworkRequestInfo("post", "https://api.shop.com/cart/items?token=secret", mapOf("Authorization" to listOf("Bearer x")))

    private fun text(metadata: dev.journeyscope.core.model.DiagnosticMetadata, key: String) = (metadata[key] as? MetadataValue.Text)?.value

    @Test
    fun successCreatesNetworkSpanUnderTheActiveParent() {
        val tracer = testTracer()
        val tracing = NetworkTracing(tracer = { tracer })
        val journey = tracer.startJourney("Add to Cart")
        val parent = journey.beginSpan("Repo", SpanCategory.REPOSITORY)
        val context = journey.context(parent)[TraceContext]

        val call = tracing.begin(request, context)
        call.complete(NetworkResponseInfo(200, contentLength = 12, protocol = "h2"))

        val span = journey.trace().span("POST api.shop.com/cart/items")
        assertEquals(SpanCategory.NETWORK, span.category)
        assertEquals(parent.id, span.parentId)
        assertEquals(SpanStatus.OK, span.status)
        assertEquals(MetadataValue.Integer(200), span.metadata["http.status"])
        assertEquals(MetadataValue.Integer(12), span.metadata["http.response.bytes"])
        assertEquals("token", text(span.metadata, "http.query.keys"))
        assertTrue(span.metadata.entries.values.none { (it as? MetadataValue.Text)?.value?.contains("secret") == true })
        assertFalse(span.metadata.entries.keys.any { it.contains("header") })
    }

    @Test
    fun httpFailureIsClassifiedAndFailsTheSpan() {
        val tracer = testTracer()
        val tracing = NetworkTracing(tracer = { tracer })
        val journey = tracer.startJourney("j")

        tracing.begin(request, journey.context()[TraceContext]).complete(NetworkResponseInfo(503))

        val span = journey.trace().spans.single()
        assertEquals(SpanStatus.FAILED, span.status)
        assertEquals("HTTP_SERVER_ERROR", text(span.metadata, "http.failure"))
        assertNotNull(span.failure)
    }

    @Test
    fun businessResultIsRecordedSeparatelyFromHttpStatus() {
        val tracer = testTracer()
        val validator = BusinessResponseValidator { _, response ->
            if (response.bodySnippet?.contains("1001") == true) BusinessResult(BusinessStatus.SESSION_EXPIRED, "1001") else null
        }
        val tracing = NetworkTracing(NetworkTraceConfig(businessValidator = validator), tracer = { tracer })
        val journey = tracer.startJourney("j")

        tracing.begin(request, journey.context()[TraceContext]).complete(NetworkResponseInfo(200, bodySnippet = """{"returnCode":"1001"}"""))

        val span = journey.trace().spans.single()
        assertEquals(SpanStatus.OK, span.status)
        assertEquals("SESSION_EXPIRED", text(span.metadata, "business.status"))
        assertEquals("1001", text(span.metadata, "returnCode"))
        assertFalse(span.metadata.entries.values.any { (it as? MetadataValue.Text)?.value?.contains("returnCode\":") == true })
    }

    @Test
    fun throwingValidatorDoesNotChangeTransportOutcome() {
        val tracer = testTracer()
        val tracing = NetworkTracing(NetworkTraceConfig(businessValidator = { _, _ -> error("boom") }), tracer = { tracer })
        val journey = tracer.startJourney("j")

        tracing.begin(request, journey.context()[TraceContext]).complete(NetworkResponseInfo(200))

        assertEquals(SpanStatus.OK, journey.trace().spans.single().status)
    }

    @Test
    fun transportFailureAndCancellationCloseTheSpan() {
        val tracer = testTracer()
        val tracing = NetworkTracing(tracer = { tracer })
        val journey = tracer.startJourney("j")
        val context = journey.context()[TraceContext]

        tracing.begin(request, context).fail(IllegalStateException("x"))
        tracing.begin(request, context).fail(CancellationException("c"))

        val spans = journey.trace().spans
        assertEquals(listOf(SpanStatus.FAILED, SpanStatus.CANCELLED), spans.map { it.status })
        assertEquals(listOf("UNKNOWN", "CANCELLED"), spans.map { text(it.metadata, "http.failure") })
        assertTrue(spans.none { it.isOpen })
    }

    @Test
    fun secondCompletionIsIgnored() {
        val tracer = testTracer()
        val tracing = NetworkTracing(tracer = { tracer })
        val journey = tracer.startJourney("j")
        val call = tracing.begin(request, journey.context()[TraceContext])

        call.complete(NetworkResponseInfo(200))
        call.fail(IllegalStateException())

        assertEquals(SpanStatus.OK, journey.trace().spans.single().status)
    }

    @Test
    fun noJourneyIgnoreRecordsNothing() {
        val tracer = testTracer()
        val other = tracer.startJourney("unrelated")
        val tracing = NetworkTracing(tracer = { tracer })

        val call = tracing.begin(request, context = null)
        call.complete(NetworkResponseInfo(200))

        assertFalse(call.isRecording)
        assertTrue(other.trace().spans.isEmpty())
        assertEquals(1, tracer.recorder.snapshot().size)
    }

    @Test
    fun noJourneyStandaloneCreatesItsOwnTrace() {
        val tracer = testTracer()
        val tracing = NetworkTracing(NetworkTraceConfig(noJourneyPolicy = NoJourneyPolicy.Standalone), tracer = { tracer })

        tracing.begin(request, context = null).complete(NetworkResponseInfo(200))

        val trace = tracer.recorder.snapshot().single()
        assertEquals(TraceStatus.COMPLETED, trace.status)
        assertEquals(1, trace.spans.size)
        assertEquals(SpanCategory.NETWORK, trace.spans.single().category)
    }

    @Test
    fun noJourneyExplicitJoinsTheSuppliedJourneyAtRoot() {
        val tracer = testTracer()
        val target = tracer.startJourney("target")
        val tracing = NetworkTracing(NetworkTraceConfig(noJourneyPolicy = NoJourneyPolicy.Explicit(target)), tracer = { tracer })

        tracing.begin(request, context = null).complete(NetworkResponseInfo(200))

        assertEquals(null, target.trace().spans.single().parentId)
    }

    @Test
    fun propagationHeadersOnlyForTrustedHosts() {
        val tracer = testTracer()
        val propagation = PropagationConfig(setOf("api.shop.com"))
        val tracing = NetworkTracing(NetworkTraceConfig(propagation = propagation), tracer = { tracer })
        val journey = tracer.startJourney("j")
        val context = journey.context()[TraceContext]

        val trusted = tracing.begin(request, context)
        val untrusted = tracing.begin(NetworkRequestInfo("GET", "https://cdn.other.com/a"), context)

        assertEquals(journey.id.value, trusted.propagationHeaders["X-JourneyScope-Trace-Id"])
        assertTrue(untrusted.propagationHeaders.isEmpty())
    }

    @Test
    fun nonRecordingJourneyContextIsInert() {
        val tracer = testTracer(config = dev.journeyscope.core.JourneyScopeConfig(enabled = false))
        val journey = tracer.startJourney("j")
        val tracing = NetworkTracing(NetworkTraceConfig(noJourneyPolicy = NoJourneyPolicy.Standalone), tracer = { tracer })

        val call = tracing.begin(request, journey.context()[TraceContext])

        assertFalse(call.isRecording)
    }
}
