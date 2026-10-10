package dev.journeyscope.sample

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.journeyscope.sample.shop.SampleGraph
import dev.journeyscope.sample.shop.enableJourneyScopeForSample
import dev.journeyscope.sample.shop.ui.ProductDetailScreen
import dev.journeyscope.ui.JourneyScopeInspector
import dev.journeyscope.ui.demo.DemoJourneys
import dev.journeyscope.ui.mapping.InspectorThresholds
import dev.journeyscope.ui.theme.defaultJourneyColors
import kotlin.time.Duration.Companion.seconds

private enum class InspectorMode { Closed, Live, Demo }

@Composable
fun App() {
    MaterialTheme {
        remember { enableJourneyScopeForSample() }
        var mode by rememberSaveable { mutableStateOf(InspectorMode.Closed) }
        when (mode) {
            InspectorMode.Closed -> ProductDetailScreen(
                viewModel = SampleGraph.productViewModel,
                onOpenInspector = { mode = InspectorMode.Live },
                onOpenDemo = { mode = InspectorMode.Demo },
            )
            InspectorMode.Live -> JourneyScopeInspector(onClose = { mode = InspectorMode.Closed })
            InspectorMode.Demo -> JourneyScopeInspector(
                source = DemoJourneys.source,
                thresholds = InspectorThresholds(slowJourney = 1.seconds),
                colors = defaultJourneyColors(),
                onClose = { mode = InspectorMode.Closed },
            )
        }
    }
}
