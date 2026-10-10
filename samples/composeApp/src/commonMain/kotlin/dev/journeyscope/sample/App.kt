package dev.journeyscope.sample

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.journeyscope.core.Journey
import dev.journeyscope.flow.LifecycleSource
import dev.journeyscope.sample.navigation.Routes
import dev.journeyscope.sample.navigation.SampleNavigator
import dev.journeyscope.sample.shop.SampleGraph
import dev.journeyscope.sample.shop.enableJourneyScopeForSample
import dev.journeyscope.sample.shop.ui.CartScreen
import dev.journeyscope.sample.shop.ui.ProductDetailScreen
import dev.journeyscope.ui.JourneyScopeInspector
import dev.journeyscope.ui.demo.DemoJourneys
import dev.journeyscope.ui.mapping.InspectorThresholds
import dev.journeyscope.ui.theme.defaultJourneyColors
import kotlin.time.Duration.Companion.seconds

@Composable
fun App(lifecycleSource: LifecycleSource? = null) {
    MaterialTheme {
        remember { enableJourneyScopeForSample() }
        val viewModel = SampleGraph.productViewModel
        val navigator = rememberSaveable(saver = SampleNavigator.saver(viewModel::observedJourney)) {
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
            Routes.CART -> CartScreen(
                viewModel = viewModel,
                onBack = { navigator.pop() },
                onShown = {
                    navigator.markShown(Routes.CART, pendingJourney.take())
                    viewModel.onCartShown()
                },
                lifecycleSource = lifecycleSource,
            )
            Routes.INSPECTOR_LIVE -> JourneyScopeInspector(onClose = { navigator.pop() })
            Routes.INSPECTOR_DEMO -> JourneyScopeInspector(
                source = DemoJourneys.source,
                thresholds = InspectorThresholds(slowJourney = 1.seconds),
                colors = defaultJourneyColors(),
                onClose = { navigator.pop() },
            )
            else -> ProductDetailScreen(
                viewModel = viewModel,
                onOpenInspector = { navigator.push(Routes.INSPECTOR_LIVE) },
                onOpenDemo = { navigator.push(Routes.INSPECTOR_DEMO) },
                onOpenCart = { navigator.push(Routes.CART) },
                lifecycleSource = lifecycleSource,
            )
        }
    }
}

private class PendingJourney {
    var value: Journey? = null

    fun take(): Journey? = value.also { value = null }
}
