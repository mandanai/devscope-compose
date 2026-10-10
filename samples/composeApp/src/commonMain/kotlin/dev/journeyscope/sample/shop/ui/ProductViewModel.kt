package dev.journeyscope.sample.shop.ui

import dev.journeyscope.core.Journey
import dev.journeyscope.core.JourneyScope
import dev.journeyscope.core.JourneyTracer
import dev.journeyscope.core.model.SpanCategory
import dev.journeyscope.core.model.diagnosticMetadata
import dev.journeyscope.sample.shop.CartRejectedException
import dev.journeyscope.sample.shop.LatencyProfile
import dev.journeyscope.sample.shop.SampleScenario
import dev.journeyscope.sample.shop.SimulatedNetworkException
import dev.journeyscope.sample.shop.domain.AddToCartUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class Product(val id: String, val name: String, val price: String)

class ProductViewModel(
    val product: Product,
    private val addToCartUseCase: AddToCartUseCase,
    private val scope: CoroutineScope,
    private val latency: LatencyProfile = LatencyProfile.Default,
    private val renderTimeout: Duration = 2.seconds,
    private val tracer: () -> JourneyTracer = { JourneyScope.tracer },
) {
    private val mutableState = MutableStateFlow(CartUiState())
    val state: StateFlow<CartUiState> = mutableState.asStateFlow()

    private val renderedToken = MutableStateFlow(0L)
    private val activeJobs = MutableStateFlow<Set<Job>>(emptySet())

    /** The journey starts here, synchronously in the click handler, and ends once the render checkpoint resolves. */
    @OptIn(DelicateCoroutinesApi::class)
    fun addToCart(scenario: SampleScenario = SampleScenario.Normal): Job {
        val journey = tracer().startJourney(
            "Add to Cart",
            diagnosticMetadata {
                put("scenario", scenario.name)
                put("productId", product.id)
                put("simulated", true)
            },
        )
        journey.recordEvent(
            "Add to Cart tapped",
            SpanCategory.UI_INTERACTION,
            diagnosticMetadata {
                put("screen", "ProductDetailScreen")
                put("control", "add_to_cart")
            },
        )
        emit(journey) { it.adding() }

        // ATOMIC: a scope cancelled before dispatch must still reach execute() so the journey gets a terminal status.
        val job = scope.launch(start = CoroutineStart.ATOMIC) { execute(journey, scenario) }
        activeJobs.update { it + job }
        job.invokeOnCompletion { activeJobs.update { jobs -> jobs - job } }
        if (scenario == SampleScenario.Cancelled) {
            scope.launch {
                delay(latency.cancelAfter)
                job.cancel()
            }
        }
        return job
    }

    fun cancelAdd() {
        activeJobs.value.forEach { it.cancel() }
    }

    /**
     * Called by the UI after Compose has drawn the frame that contains the state with [token].
     * Confirms Compose finished composition, layout and draw for that state; it does not confirm display scan-out.
     * Also resolves checkpoints of earlier tokens, because Compose may draw only the latest of two quick emissions.
     */
    fun onCartCountRendered(token: Long) {
        renderedToken.update { maxOf(it, token) }
    }

    private suspend fun execute(journey: Journey, scenario: SampleScenario) {
        try {
            withContext(journey.context()) {
                var failure: Throwable? = null
                val token = try {
                    val count = journey.traceSuspend("ProductViewModel.addToCart", SpanCategory.VIEW_MODEL) {
                        addToCartUseCase(product.id, 1, scenario)
                    }
                    emit(journey) { it.added(count) }
                } catch (e: CartRejectedException) {
                    failure = e
                    emit(journey) { it.failed(e.reason.userMessage) }
                } catch (e: SimulatedNetworkException) {
                    failure = e
                    emit(journey) { it.failed(NETWORK_MESSAGE) }
                }
                awaitRender(journey, token)
                if (failure == null) journey.finish() else journey.fail(failure)
            }
        } catch (cancellation: CancellationException) {
            emit(journey) { it.cancelled() }
            journey.cancel()
            throw cancellation
        } catch (unexpected: Throwable) {
            emit(journey) { it.failed(UNEXPECTED_MESSAGE) }
            journey.fail(unexpected)
            throw unexpected
        }
    }

    private fun emit(journey: Journey, transform: (CartUiState) -> CartUiState): Long {
        val next = mutableState.updateAndGet { current ->
            transform(current).let { it.copy(renderToken = current.renderToken + 1) }
        }
        journey.recordEvent(
            "CartUiState emitted",
            SpanCategory.STATE_EMISSION,
            diagnosticMetadata {
                put("phase", next.phase.name)
                put("cartCount", next.cartCount)
                put("renderToken", next.renderToken)
            },
        )
        return next.renderToken
    }

    private suspend fun awaitRender(journey: Journey, token: Long) {
        if (!journey.isRecording) return
        val span = journey.beginSpan("Cart count render", SpanCategory.UI_RENDER, diagnosticMetadata { put("renderToken", token) })
        val confirmed = withTimeoutOrNull(renderTimeout) { renderedToken.first { it >= token } } != null
        if (!confirmed) {
            span.recordEvent(
                "Render checkpoint not confirmed",
                SpanCategory.UI_RENDER,
                diagnosticMetadata {
                    put("renderConfirmed", false)
                    put("timeoutMs", renderTimeout.inWholeMilliseconds)
                },
            )
        }
        span.end()
    }

    private companion object {
        const val NETWORK_MESSAGE = "Network unavailable"
        const val UNEXPECTED_MESSAGE = "Unexpected error"
    }
}
