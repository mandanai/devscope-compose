package dev.journeyscope.ui.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.journeyscope.core.model.JourneyTrace
import dev.journeyscope.ui.JourneyScopeInspector
import dev.journeyscope.ui.demo.DemoJourneys
import dev.journeyscope.ui.mapping.InspectorThresholds
import dev.journeyscope.ui.state.staticTraceSource
import dev.journeyscope.ui.theme.JourneyColors
import kotlin.time.Duration.Companion.seconds

private val PreviewThresholds = InspectorThresholds(slowJourney = 1.seconds)

@Composable
private fun PreviewHost(
    traces: List<JourneyTrace>,
    dark: Boolean = false,
) {
    val source = remember(traces) { staticTraceSource(traces, DemoJourneys.NOW_NANOS) }
    Box(Modifier.fillMaxSize()) {
        JourneyScopeInspector(
            source = source,
            thresholds = PreviewThresholds,
            colors = if (dark) JourneyColors.dark() else JourneyColors.light(),
        )
    }
}

@Preview
@Composable
private fun CompletedJourneyList() = PreviewHost(listOf(DemoJourneys.openProduct(), DemoJourneys.addToCart()))

@Preview
@Composable
private fun SlowJourneyList() = PreviewHost(listOf(DemoJourneys.slowJourney()), dark = true)

@Preview
@Composable
private fun FailedJourneyList() = PreviewHost(listOf(DemoJourneys.checkoutFailed()))

@Preview
@Composable
private fun RunningJourneyList() = PreviewHost(listOf(DemoJourneys.syncOrdersRunning(), DemoJourneys.syncProfileRunning()))

@Preview
@Composable
private fun EmptyJourneyList() = PreviewHost(emptyList())

@Preview
@Composable
private fun LargeJourneyList() = PreviewHost(listOf(DemoJourneys.importCatalogLarge()))

@Preview
@Composable
private fun ParallelRequestsList() = PreviewHost(listOf(DemoJourneys.parallelRequests(), DemoJourneys.addToCartWithoutCheckpoint()))
