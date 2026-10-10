package dev.journeyscope.sample.shop

import dev.journeyscope.core.JourneyScopeConfig
import dev.journeyscope.core.JourneyTracer
import dev.journeyscope.core.model.JourneySpan
import dev.journeyscope.core.model.JourneyTrace
import dev.journeyscope.core.model.SpanCategory
import dev.journeyscope.core.recorder.InMemoryJourneyRecorder
import dev.journeyscope.core.timing.JourneyClock
import dev.journeyscope.sample.shop.data.CartLocalDataSource
import dev.journeyscope.sample.shop.data.CartRemoteDataSource
import dev.journeyscope.sample.shop.data.CartRepository
import dev.journeyscope.sample.shop.data.SimulatedCartApi
import dev.journeyscope.sample.shop.domain.AddToCartUseCase
import dev.journeyscope.sample.shop.ui.Product
import dev.journeyscope.sample.shop.ui.ProductViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.time.Duration

/** Monotonic time is the test scheduler's virtual time, so recorded durations equal the simulated delays exactly. */
@OptIn(ExperimentalCoroutinesApi::class)
class TestSchedulerClock(private val scheduler: TestCoroutineScheduler) : JourneyClock {
    override fun monotonicNanos(): Long = scheduler.currentTime * NANOS_PER_MILLI

    override fun wallClockMillis(): Long = WALL_EPOCH + scheduler.currentTime

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val WALL_EPOCH = 1_700_000_000_000L
    }
}

val TestProduct = Product("sku-test", "Test Product", "$1.00")

@OptIn(ExperimentalCoroutinesApi::class)
class VirtualShop(
    testScope: TestScope,
    val latency: LatencyProfile = LatencyProfile(),
    remote: CartRemoteDataSource? = null,
    scope: CoroutineScope = testScope,
    renderTimeout: Duration = Duration.parse("2s"),
) {
    val recorder = InMemoryJourneyRecorder(maxTraces = 50)
    val ioDispatcher: CoroutineDispatcher = StandardTestDispatcher(testScope.testScheduler, "io")
    private val tracer = JourneyTracer(
        config = JourneyScopeConfig(enabled = true),
        recorder = recorder,
        clock = TestSchedulerClock(testScope.testScheduler),
    )
    val viewModel = ProductViewModel(
        product = TestProduct,
        addToCartUseCase = AddToCartUseCase(
            CartRepository(remote ?: SimulatedCartApi(latency), CartLocalDataSource(latency), ioDispatcher),
        ),
        scope = scope,
        latency = latency,
        renderTimeout = renderTimeout,
        tracer = { tracer },
    )

    /** Stands in for the screen: acknowledges every emitted state as drawn. */
    fun autoRender(testScope: TestScope) {
        testScope.backgroundScope.launch {
            viewModel.state.collect { viewModel.onCartCountRendered(it.renderToken) }
        }
    }

    fun traces(): List<JourneyTrace> = recorder.snapshot()

    fun onlyTrace(): JourneyTrace = traces().single()
}

fun JourneyTrace.span(name: String): JourneySpan = spans.single { it.name == name }

fun JourneyTrace.spans(category: SpanCategory): List<JourneySpan> = spans.filter { it.category == category }

val JourneySpan.duration: Duration
    get() = Duration.parse("${(checkNotNull(endNanos) { "span still open" } - startNanos) / NANOS_PER_MICRO}us")

private const val NANOS_PER_MICRO = 1_000L
