package dev.journeyscope.sample.shop

import dev.journeyscope.core.expectation.ExpectedSequence
import dev.journeyscope.core.expectation.ExpectedStep
import dev.journeyscope.core.model.JourneyEvent
import dev.journeyscope.core.model.MetadataValue
import dev.journeyscope.core.model.SpanCategory
import dev.journeyscope.core.model.TraceStatus
import dev.journeyscope.flow.observeWithJourneyScope
import dev.journeyscope.sample.navigation.Routes
import dev.journeyscope.sample.navigation.SampleNavigator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StateNavigationJourneyTest {
    private fun JourneyEvent.text(key: String): String? = (metadata[key] as? MetadataValue.Text)?.value

    private fun TestScope.screens(shop: VirtualShop): SampleNavigator {
        val navigator = SampleNavigator(shop.viewModel::observedJourney)
        backgroundScope.launch {
            shop.viewModel.state.observeWithJourneyScope("cart-ui-state", shop.viewModel.traceOptions()) {
                shop.viewModel.onCartCountRendered(it.renderToken)
            }
        }
        backgroundScope.launch {
            shop.viewModel.openCartRequests.collect { journey ->
                navigator.push(Routes.CART, journey)
                navigator.markShown(Routes.CART, journey)
                shop.viewModel.onCartShown()
            }
        }
        runCurrent()
        return navigator
    }

    @Test
    fun stateObservationAndNavigationJoinTheAddToCartJourney() = runTest {
        val shop = VirtualShop(this)
        val navigator = screens(shop)

        shop.viewModel.addToCart(openCartOnSuccess = true)
        advanceUntilIdle()

        val trace = shop.onlyTrace()
        assertEquals(TraceStatus.COMPLETED, trace.status)
        assertEquals(Routes.CART, navigator.current)

        val observed = trace.events.filter { it.text("state.event") == "observed" }
        assertEquals(listOf("Adding · 0", "Added · 1"), observed.map { it.text("state.current") })
        assertTrue(trace.events.any { it.category == SpanCategory.NAVIGATION && it.text("nav.action") == "Navigate" })
        assertTrue(trace.events.any { it.metadata["nav.completed"] == MetadataValue.Flag(true) })

        val sequence = ExpectedSequence(
            "add-to-cart-then-cart",
            listOf(
                ExpectedStep.named("Tap", "Add to Cart tapped"),
                ExpectedStep.category("API", SpanCategory.NETWORK),
                ExpectedStep.category("UI collector observed", SpanCategory.STATE, "cart-ui-state state observed"),
                ExpectedStep.category("Navigation", SpanCategory.NAVIGATION, "Navigate"),
            ),
        )
        assertTrue(sequence.evaluate(trace).missing.isEmpty())
    }

    @Test
    fun plainAddRecordsStateButNoNavigation() = runTest {
        val shop = VirtualShop(this)
        screens(shop)

        shop.viewModel.addToCart()
        advanceUntilIdle()

        val trace = shop.onlyTrace()
        assertEquals(TraceStatus.COMPLETED, trace.status)
        assertTrue(trace.events.none { it.category == SpanCategory.NAVIGATION })
        assertTrue(trace.events.any { it.text("state.event") == "observed" })
    }

    @Test
    fun missingNavigationIsOnlyReportedWhenExpected() = runTest {
        val shop = VirtualShop(this)
        screens(shop)

        shop.viewModel.addToCart()
        advanceUntilIdle()

        val expectation = ExpectedSequence("needs-nav", listOf(ExpectedStep.category("Navigation", SpanCategory.NAVIGATION)))
        assertEquals(listOf("Navigation"), expectation.evaluate(shop.onlyTrace()).missing)
        assertEquals(TraceStatus.COMPLETED, shop.onlyTrace().status)
    }
}
