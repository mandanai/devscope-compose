package dev.chayaphon.journeyscope.sample

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.chayaphon.journeyscope.core.Journey
import dev.chayaphon.journeyscope.core.JourneyScope
import dev.chayaphon.journeyscope.core.JourneyScopeConfig
import dev.chayaphon.journeyscope.flow.LifecycleSource
import dev.chayaphon.journeyscope.sample.navigation.Routes
import dev.chayaphon.journeyscope.sample.navigation.SampleNavigator
import dev.chayaphon.journeyscope.sample.shop.SampleGraph
import dev.chayaphon.journeyscope.sample.shop.ui.CartScreen
import dev.chayaphon.journeyscope.sample.shop.ui.ProductDetailScreen

@Composable
fun App(
    isDebuggable: Boolean = true,
    lifecycleSource: LifecycleSource? = null,
    inspector: SampleInspector? = null,
) {
    MaterialTheme {
        remember { if (!JourneyScope.isEnabled) JourneyScope.configure(JourneyScopeConfig.debugOnly(isDebuggable)) }
        val viewModel = SampleGraph.productViewModel
        val navigator =
            rememberSaveable(saver = SampleNavigator.saver(viewModel::observedJourney)) {
                SampleNavigator(viewModel::observedJourney)
            }
        val pendingJourney = remember { PendingJourney() }

        LaunchedEffect(viewModel) {
            viewModel.openCartRequests.collect { journey ->
                pendingJourney.value = journey
                navigator.push(Routes.CART, journey)
            }
        }
        NavigationBackHandler(
            state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
            isBackEnabled = navigator.current == Routes.CART,
            onBackCompleted = { navigator.pop() },
        )

        when (navigator.current) {
            Routes.CART -> {
                CartScreen(
                    viewModel = viewModel,
                    onBack = { navigator.pop() },
                    onShown = {
                        navigator.markShown(Routes.CART, pendingJourney.take())
                        viewModel.onCartShown()
                    },
                    lifecycleSource = lifecycleSource,
                )
            }

            Routes.INSPECTOR_LIVE -> {
                inspector?.Live(onClose = { navigator.pop() })
            }

            Routes.INSPECTOR_DEMO -> {
                inspector?.Demo(onClose = { navigator.pop() })
            }

            else -> {
                ProductDetailScreen(
                    viewModel = viewModel,
                    onOpenInspector = inspector?.let { { navigator.push(Routes.INSPECTOR_LIVE) } },
                    onOpenDemo = inspector?.let { { navigator.push(Routes.INSPECTOR_DEMO) } },
                    onOpenCart = { navigator.push(Routes.CART) },
                    lifecycleSource = lifecycleSource,
                )
            }
        }
    }
}

private class PendingJourney {
    var value: Journey? = null

    fun take(): Journey? = value.also { value = null }
}
