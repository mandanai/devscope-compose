package dev.journeyscope.export

import dev.journeyscope.core.model.MetadataValue
import dev.journeyscope.core.model.diagnosticMetadata
import dev.journeyscope.export.policy.ExportPolicy
import dev.journeyscope.export.redaction.RedactionPipeline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RedactionPipelineTest {
    private val pipeline = RedactionPipeline()

    @Test
    fun dropsKeysOutsideTheAllowlist() {
        val trace =
            sampleTrace(
                metadata =
                    diagnosticMetadata {
                        put("cartContents", "secret shoes")
                        put("http.method", "GET")
                    },
            )
        val redacted = pipeline.redact(trace)
        assertNull(redacted.trace.metadata["cartContents"])
        assertEquals(MetadataValue.Text("GET"), redacted.trace.metadata["http.method"])
        assertEquals(2, redacted.report.droppedKeys)
    }

    @Test
    fun masksSensitiveAllowlistedKeysAndScrubsValues() {
        val metadata =
            diagnosticMetadata {
                put("http.path", "/login?token=abc123&page=2")
                put("http.host", "api.example.com")
                put("http.refresh", "x")
            }
        val allow =
            ExportPolicy(
                allowlist =
                    dev.journeyscope.export.redaction
                        .MetadataAllowlist(setOf("http.*")),
            )
        val redacted = RedactionPipeline(allow).redact(sampleTrace(metadata = metadata)).trace.metadata
        val path = (redacted["http.path"] as MetadataValue.Text).value
        assertFalse(path.contains("abc123"))
        assertTrue(path.contains("page=2"))
        assertEquals(MetadataValue.Text("[REDACTED]"), redacted["http.refresh"])
    }

    @Test
    fun removesAuthorizationFromSpansEvenWhenCapturedUpstream() {
        val spans = pipeline.redact(sampleTrace()).trace.spans
        assertTrue(spans.none { it.metadata["authorization"] != null })
    }

    @Test
    fun scrubsEmailsJwtsSignedUrlsAndFreeTextNames() {
        val result =
            pipeline
                .redact(
                    sampleTrace(metadata = diagnosticMetadata { put("http.path", "/u/jane@example.com/eyJhbGciOiJI.eyJzdWIiOiIx.sigsigsig") }),
                ).trace.metadata["http.path"] as MetadataValue.Text
        assertFalse(result.value.contains("jane@example.com"))
        assertFalse(result.value.contains("eyJhbGciOiJI"))
        val signed =
            dev.journeyscope.export.redaction.ValueScrubber.scrub(
                "https://b.s3.amazonaws.com/o?X-Amz-Signature=deadbeef&X-Amz-Expires=60&id=7",
            )
        assertFalse(signed.text.contains("deadbeef"))
        assertTrue(signed.text.contains("id=7"))
    }

    @Test
    fun dropsBodyKeysUnlessExplicitlyAllowed() {
        val metadata = diagnosticMetadata { put("http.request.body", "{\"card\":\"4111\"}") }
        val dropped = pipeline.redact(sampleTrace(metadata = metadata))
        assertNull(dropped.trace.metadata["http.request.body"])
        assertEquals(1, dropped.report.droppedBodies)
    }

    @Test
    fun stripsFailureMessagesAndStacks() {
        val failure =
            dev.journeyscope.core.model
                .FailureInfo("IOException", "user 42 not found", "at a.b.C")
        val trace = sampleTrace().copy(failure = failure)
        val redacted = pipeline.redact(trace).trace.failure
        assertEquals("IOException", redacted?.type)
        assertNull(redacted?.message)
        assertNull(redacted?.stackTrace)
    }
}
