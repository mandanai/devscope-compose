package dev.journeyscope.sample.shop

import dev.journeyscope.core.model.MetadataValue
import dev.journeyscope.core.model.SpanCategory
import dev.journeyscope.diagnostics.DiagnosticAnalyzer
import dev.journeyscope.diagnostics.DiagnosticConfig
import dev.journeyscope.diagnostics.DiagnosticSeverity
import dev.journeyscope.core.model.SpanStatus
import dev.journeyscope.core.model.TraceStatus
import dev.journeyscope.sample.shop.data.CartApiResponse
import dev.journeyscope.sample.shop.data.CartRemoteDataSource
import dev.journeyscope.sample.shop.ui.CartPhase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class AddToCartJourneyTest {
    @Test
    fun normalFlowExecutesEveryLayerInOrder() = runTest {
        val shop = VirtualShop(this).also { it.autoRender(this) }

        shop.viewModel.addToCart()
        advanceUntilIdle()

        val trace = shop.onlyTrace()
        assertEquals(TraceStatus.COMPLETED, trace.status)
        assertEquals(
            setOf(
                SpanCategory.VIEW_MODEL, SpanCategory.USE_CASE, SpanCategory.REPOSITORY, SpanCategory.NETWORK,
                SpanCategory.BUSINESS_VALIDATION, SpanCategory.DATABASE, SpanCategory.UI_RENDER,
            ),
            trace.spans.map { it.category }.toSet(),
        )
        val events = trace.events.map { it.category to it.name }
        assertTrue(SpanCategory.UI_INTERACTION to "Add to Cart tapped" in events)
        assertTrue(events.count { it.first == SpanCategory.STATE_EMISSION } >= 2)
        assertTrue(trace.spans.all { it.status == SpanStatus.OK })
    }

    @Test
    fun spansFormTheExpectedParentChildTree() = runTest {
        val shop = VirtualShop(this).also { it.autoRender(this) }

        shop.viewModel.addToCart()
        advanceUntilIdle()

        val trace = shop.onlyTrace()
        val viewModel = trace.span("ProductViewModel.addToCart")
        val useCase = trace.span("AddToCartUseCase")
        val repository = trace.span("CartRepository.add")
        assertNull(viewModel.parentId)
        assertEquals(viewModel.id, useCase.parentId)
        assertEquals(useCase.id, repository.parentId)
        listOf(CART_API_SPAN, "validateCartResponse", "cart.upsert").forEach { name ->
            assertEquals(repository.id, trace.span(name).parentId, name)
        }
        assertNull(trace.span("Cart count render").parentId)
        val transaction = trace.span("cart.upsert")
        listOf("cart_items.quantityFor", "cart_items.upsert", "cart_items.totalQuantity").forEach { name ->
            assertEquals(transaction.id, trace.span(name).parentId, name)
        }
    }

    @Test
    fun databaseSpanSharesTheJourneyAndNeverRecordsSqlOrValues() = runTest {
        val shop = VirtualShop(this).also { it.autoRender(this) }

        shop.viewModel.addToCart()
        advanceUntilIdle()

        val trace = shop.onlyTrace()
        val transaction = trace.span("cart.upsert")
        assertEquals("committed", (transaction.metadata["db.transaction.outcome"] as MetadataValue.Text).value)
        trace.spans(SpanCategory.DATABASE).forEach { span ->
            assertTrue(span.metadata.entries.keys.all { it.startsWith("db.") }, span.name)
            assertTrue(span.metadata.entries.values.none { it is MetadataValue.Text && (it.value.contains("SELECT", true) || it.value.contains("sku-test")) })
        }
        val order = trace.spans.sortedBy { it.sequence }.map { it.name }
        assertTrue(order.indexOf("validateCartResponse") < order.indexOf("cart.upsert"))
        assertTrue(order.indexOf("cart.upsert") < order.indexOf("Cart count render"))
    }

    @Test
    fun diagnosticsSeeNoFindingsForTheNormalFlowAndCompletionCheckpointIsObserved() = runTest {
        val shop = VirtualShop(this).also { it.autoRender(this) }

        shop.viewModel.addToCart()
        advanceUntilIdle()

        val report = DiagnosticAnalyzer(DiagnosticConfig(expectedSequences = listOf(AddToCartCompletion))).analyze(shop.onlyTrace())
        assertTrue(report.findings.none { it.severity != DiagnosticSeverity.Info }, report.findings.map { it.ruleId }.toString())
    }

    @Test
    fun successUpdatesUiState() = runTest {
        val shop = VirtualShop(this).also { it.autoRender(this) }

        shop.viewModel.addToCart()
        advanceUntilIdle()

        val state = shop.viewModel.state.value
        assertEquals(CartPhase.Added, state.phase)
        assertEquals(1, state.cartCount)
        assertEquals(0, state.pending)
    }

    @Test
    fun recordedDurationsFollowTheConfiguredLatencies() = runTest {
        val shop = VirtualShop(this).also { it.autoRender(this) }

        shop.viewModel.addToCart()
        advanceUntilIdle()

        val trace = shop.onlyTrace()
        assertEquals(shop.latency.normal, trace.span(CART_API_SPAN).duration)
        assertEquals(shop.latency.normal, trace.summary?.duration)
        assertEquals(false, trace.summary?.isSlow)
    }

    @Test
    fun slowApiIsMeasuredAndFlaggedSlow() = runTest {
        val shop = VirtualShop(this).also { it.autoRender(this) }

        shop.viewModel.addToCart(SampleScenario.SlowApi)
        advanceUntilIdle()

        val trace = shop.onlyTrace()
        assertEquals(TraceStatus.COMPLETED, trace.status)
        assertEquals(shop.latency.slow, trace.span(CART_API_SPAN).duration)
        assertEquals(true, trace.summary?.isSlow)
    }

    @Test
    fun businessFailureEndsFailedAndNamesTheValidationSpan() = runTest {
        val shop = VirtualShop(this).also { it.autoRender(this) }

        shop.viewModel.addToCart(SampleScenario.BusinessFailure)
        advanceUntilIdle()

        val trace = shop.onlyTrace()
        assertEquals(TraceStatus.FAILED, trace.status)
        assertEquals(SpanStatus.OK, trace.span(CART_API_SPAN).status)
        val validation = trace.span("validateCartResponse")
        assertEquals(SpanStatus.FAILED, validation.status)
        assertTrue(validation.failure?.type?.endsWith("CartRejectedException") == true)
        assertNull(validation.failure?.message)
        assertNull(validation.failure?.stackTrace)
        assertTrue(trace.spans(SpanCategory.DATABASE).isEmpty())
        val state = shop.viewModel.state.value
        assertEquals(CartPhase.Failed, state.phase)
        assertEquals("Out of stock", state.message)
        assertEquals(0, state.cartCount)
    }

    @Test
    fun businessFailureIsVisibleOnTheNetworkSpanAlongsideHttp200() = runTest {
        val shop = VirtualShop(this).also { it.autoRender(this) }

        shop.viewModel.addToCart(SampleScenario.BusinessFailure)
        advanceUntilIdle()

        val network = shop.onlyTrace().span(CART_API_SPAN)
        assertEquals(MetadataValue.Integer(200), network.metadata["http.status"])
        assertEquals(MetadataValue.Text("FAILURE"), network.metadata["business.status"])
        assertEquals(MetadataValue.Text("2001"), network.metadata["returnCode"])
        assertTrue(network.metadata.entries.keys.none { it.contains("header") })
    }

    @Test
    fun networkExceptionEndsFailedAtTheNetworkSpan() = runTest {
        val shop = VirtualShop(this).also { it.autoRender(this) }

        shop.viewModel.addToCart(SampleScenario.NetworkException)
        advanceUntilIdle()

        val trace = shop.onlyTrace()
        assertEquals(TraceStatus.FAILED, trace.status)
        val network = trace.span(CART_API_SPAN)
        assertEquals(SpanStatus.FAILED, network.status)
        assertTrue(network.failure?.type?.endsWith("SimulatedNetworkException") == true)
        assertNull(network.failure?.message)
        assertTrue(trace.spans(SpanCategory.BUSINESS_VALIDATION).isEmpty())
        assertEquals(CartPhase.Failed, shop.viewModel.state.value.phase)
    }

    @Test
    fun cancelledOperationEndsCancelledAndRethrowsCancellation() = runTest {
        val shop = VirtualShop(this).also { it.autoRender(this) }

        val job = shop.viewModel.addToCart(SampleScenario.Cancelled)
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        val trace = shop.onlyTrace()
        assertEquals(TraceStatus.CANCELLED, trace.status)
        assertEquals(SpanStatus.CANCELLED, trace.span(CART_API_SPAN).status)
        assertEquals(shop.latency.cancelAfter, trace.span(CART_API_SPAN).duration)
        assertTrue(trace.spans.none { it.status == SpanStatus.RUNNING })
        assertTrue(trace.spans(SpanCategory.BUSINESS_VALIDATION).isEmpty())
        val state = shop.viewModel.state.value
        assertEquals(CartPhase.Cancelled, state.phase)
        assertEquals(0, state.pending)
    }

    @Test
    fun cancelButtonCancelsInFlightAdds() = runTest {
        val shop = VirtualShop(this).also { it.autoRender(this) }

        shop.viewModel.addToCart(SampleScenario.SlowApi)
        advanceTimeBy(100.milliseconds)
        shop.viewModel.cancelAdd()
        advanceUntilIdle()

        assertEquals(TraceStatus.CANCELLED, shop.onlyTrace().status)
        assertEquals(CartPhase.Cancelled, shop.viewModel.state.value.phase)
    }

    @Test
    fun concurrentAddsGetSeparateTracesAndOneAckConfirmsBothRenders() = runTest {
        val shop = VirtualShop(this)

        shop.viewModel.addToCart()
        shop.viewModel.addToCart()
        advanceTimeBy(shop.latency.normal + 1.milliseconds)
        shop.viewModel.onCartCountRendered(shop.viewModel.state.value.renderToken)
        advanceUntilIdle()

        val traces = shop.traces()
        assertEquals(2, traces.map { it.id }.toSet().size)
        traces.forEach { trace ->
            assertEquals(TraceStatus.COMPLETED, trace.status)
            assertEquals(1, trace.spans(SpanCategory.NETWORK).size)
            val ids = trace.spans.map { it.id }.toSet()
            assertTrue(trace.spans.all { it.parentId == null || it.parentId in ids })
            assertTrue(trace.events.none { it.name == "Render checkpoint not confirmed" })
            assertEquals(SpanStatus.OK, trace.span("Cart count render").status)
        }
        assertEquals(2, shop.viewModel.state.value.cartCount)
    }

    @Test
    fun phaseStaysAddingUntilTheLastConcurrentAddFinishes() = runTest {
        val shop = VirtualShop(this).also { it.autoRender(this) }

        shop.viewModel.addToCart()
        shop.viewModel.addToCart(SampleScenario.SlowApi)
        advanceTimeBy(shop.latency.normal + 1.milliseconds)
        assertEquals(CartPhase.Adding, shop.viewModel.state.value.phase)
        assertEquals(1, shop.viewModel.state.value.pending)
        advanceUntilIdle()

        val state = shop.viewModel.state.value
        assertEquals(CartPhase.Added, state.phase)
        assertEquals(2, state.cartCount)
        assertTrue(shop.traces().all { it.status == TraceStatus.COMPLETED })
    }

    @Test
    fun journeyStaysRunningUntilTheRenderCheckpointIsAcknowledged() = runTest {
        val shop = VirtualShop(this)

        shop.viewModel.addToCart()
        advanceTimeBy(shop.latency.normal + 1.milliseconds)
        assertEquals(TraceStatus.RUNNING, shop.onlyTrace().status)
        assertEquals(SpanStatus.RUNNING, shop.onlyTrace().span("Cart count render").status)

        shop.viewModel.onCartCountRendered(shop.viewModel.state.value.renderToken)
        advanceUntilIdle()

        assertEquals(TraceStatus.COMPLETED, shop.onlyTrace().status)
    }

    @Test
    fun missingRenderAcknowledgementIsRecordedAndDoesNotChangeTheOutcome() = runTest {
        val shop = VirtualShop(this)

        shop.viewModel.addToCart()
        advanceUntilIdle()

        val trace = shop.onlyTrace()
        assertEquals(TraceStatus.COMPLETED, trace.status)
        assertTrue(trace.events.any { it.name == "Render checkpoint not confirmed" })
    }

    @Test
    fun runningTraceIsVisibleInTheRecorderWhileOperationsExecute() = runTest {
        val shop = VirtualShop(this).also { it.autoRender(this) }

        shop.viewModel.addToCart(SampleScenario.SlowApi)
        advanceTimeBy(500.milliseconds)

        val running = shop.onlyTrace()
        assertEquals(TraceStatus.RUNNING, running.status)
        assertTrue(running.span(CART_API_SPAN).isOpen)

        advanceUntilIdle()
        assertTrue(shop.onlyTrace().status.isTerminal)
    }

    @Test
    fun unexpectedErrorsFailTheJourneyAndAreRethrown() = runTest {
        val thrown = mutableListOf<Throwable>()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + CoroutineExceptionHandler { _, t -> thrown += t })
        val broken = object : CartRemoteDataSource {
            override suspend fun addItem(productId: String, quantity: Int, scenario: SampleScenario): CartApiResponse =
                throw IllegalStateException("bug")
        }
        val shop = VirtualShop(this, remote = broken, scope = scope).also { it.autoRender(this) }

        shop.viewModel.addToCart()
        advanceUntilIdle()
        runCurrent()

        assertEquals(TraceStatus.FAILED, shop.onlyTrace().status)
        assertIs<IllegalStateException>(thrown.single())
    }
}
