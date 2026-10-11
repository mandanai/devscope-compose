package dev.chayaphon.journeyscope.ui

import dev.chayaphon.journeyscope.core.JourneyScopeConfig
import dev.chayaphon.journeyscope.core.JourneyTracer
import dev.chayaphon.journeyscope.core.TraceContext
import dev.chayaphon.journeyscope.core.network.BusinessResult
import dev.chayaphon.journeyscope.core.network.BusinessStatus
import dev.chayaphon.journeyscope.core.network.NetworkRequestInfo
import dev.chayaphon.journeyscope.core.network.NetworkResponseInfo
import dev.chayaphon.journeyscope.core.network.NetworkTraceConfig
import dev.chayaphon.journeyscope.core.network.NetworkTracing
import dev.chayaphon.journeyscope.diagnostics.DiagnosticAnalyzer
import dev.chayaphon.journeyscope.ui.mapping.InspectorThresholds
import dev.chayaphon.journeyscope.ui.mapping.MetadataDisplayPolicy
import dev.chayaphon.journeyscope.ui.mapping.TraceMapper
import dev.chayaphon.journeyscope.ui.model.RowState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkMappingTest {
    private val tracer = JourneyTracer(JourneyScopeConfig(enabled = true))
    private val tracing =
        NetworkTracing(
            NetworkTraceConfig(
                headerAllowlist = setOf("x-request-id", "authorization"),
                businessValidator = { _, _ -> BusinessResult(BusinessStatus.SESSION_EXPIRED, "1001") },
            ),
        ) { tracer }

    private fun detail(block: (dev.chayaphon.journeyscope.core.network.NetworkCall) -> Unit): dev.chayaphon.journeyscope.ui.model.JourneyDetailUi {
        val journey = tracer.startJourney("Checkout")
        val request =
            NetworkRequestInfo(
                "POST",
                "https://api.shop.com/orders?token=secret",
                mapOf("X-Request-Id" to listOf("r-1"), "Authorization" to listOf("Bearer top-secret")),
            )
        block(tracing.begin(request, journey.context()[TraceContext]))
        journey.finish()
        val mapper = TraceMapper(InspectorThresholds(), MetadataDisplayPolicy.Default, DiagnosticAnalyzer())
        return mapper.detail(checkNotNull(journey.snapshot()), 0L)
    }

    @Test
    fun networkRowShowsEndpointStatusAndBusinessResultWithoutSecrets() {
        val row = detail { it.complete(NetworkResponseInfo(200)) }.rows.single { it.title == "POST api.shop.com/orders" }

        assertEquals("POST /orders · 200", row.detail)
        assertEquals(RowState.Ok, row.state)
        val shown = row.metadata.associate { it.key to it.value }
        assertEquals("SESSION_EXPIRED", shown["business.status"])
        assertEquals("1001", shown["returnCode"])
        assertEquals("r-1", shown["http.request.header.x-request-id"])
        assertFalse(shown.keys.any { it.contains("authorization") })
        assertTrue(shown.values.none { it.contains("secret") })
    }

    @Test
    fun failedNetworkRowUsesReadableClassification() {
        val row = detail { it.complete(NetworkResponseInfo(503)) }.rows.single { it.title.startsWith("POST") }

        assertEquals(RowState.Failed, row.state)
        assertEquals("HTTP server error · HTTP 503", row.failureLabel)
    }

    @Test
    fun cancelledNetworkRowHasNoFailureLabel() {
        val row = detail { it.cancel() }.rows.single { it.title.startsWith("POST") }

        assertEquals(RowState.Cancelled, row.state)
        assertNull(row.failureLabel)
    }
}
