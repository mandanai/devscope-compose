package dev.journeyscope.sample.shop

import dev.journeyscope.core.JourneyScopeConfig
import dev.journeyscope.core.JourneyTracer
import dev.journeyscope.core.model.TraceStatus
import dev.journeyscope.core.recorder.InMemoryJourneyRecorder
import dev.journeyscope.sample.shop.data.CartLocalDataSource
import dev.journeyscope.sample.shop.data.CartRepository
import dev.journeyscope.sample.shop.data.KtorCartApi
import dev.journeyscope.sample.shop.data.sampleHttpClient
import dev.journeyscope.sample.shop.domain.AddToCartUseCase
import dev.journeyscope.sample.shop.ui.ProductViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** Runs on real dispatchers and the system monotonic clock to prove durations are measured, not scripted. */
class RealClockJourneyTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun traceSurvivesDispatcherSwitchesAndMeasuresRealTime() = runTest {
        val latency = LatencyProfile(normal = 40.milliseconds, database = 20.milliseconds)
        val recorder = InMemoryJourneyRecorder()
        val tracer = JourneyTracer(JourneyScopeConfig(enabled = true), recorder)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = CartRepository(
            KtorCartApi(sampleHttpClient(latency, { tracer })),
            CartLocalDataSource(latency),
            Dispatchers.Default.limitedParallelism(1, "test-io"),
        )
        val viewModel = ProductViewModel(
            product = TestProduct,
            addToCartUseCase = AddToCartUseCase(repository),
            scope = scope,
            latency = latency,
            renderTimeout = 50.milliseconds,
            tracer = { tracer },
        )

        withContext(Dispatchers.Default) { viewModel.addToCart().join() }

        val trace = recorder.snapshot().single()
        assertEquals(TraceStatus.COMPLETED, trace.status)
        assertEquals(6, trace.spans.count { it.name != "Cart count render" })
        assertTrue(trace.span(CART_API_SPAN).duration >= latency.normal)
        assertTrue(trace.span("cart.upsert").duration >= latency.database)
        assertTrue(checkNotNull(trace.summary).duration >= latency.normal + latency.database)
    }
}
