package dev.chayaphon.journeyscope.sample

import androidx.compose.runtime.Composable
import dev.chayaphon.journeyscope.sample.shop.AddToCartCompletion
import dev.chayaphon.journeyscope.sample.shop.SampleExport
import dev.chayaphon.journeyscope.ui.JourneyScopeInspector
import dev.chayaphon.journeyscope.ui.demo.DemoJourneys
import dev.chayaphon.journeyscope.ui.mapping.InspectorThresholds
import dev.chayaphon.journeyscope.ui.theme.defaultJourneyColors
import kotlin.time.Duration.Companion.seconds

internal object IosInspector : SampleInspector {
    @Composable
    override fun Live(onClose: () -> Unit) {
        JourneyScopeInspector(
            expectedSequences = listOf(AddToCartCompletion),
            onClose = onClose,
            exportController = SampleExport.controller,
        )
    }

    @Composable
    override fun Demo(onClose: () -> Unit) {
        JourneyScopeInspector(
            source = DemoJourneys.source,
            expectedSequences = listOf(DemoJourneys.addToCartCompletion),
            thresholds = InspectorThresholds(slowJourney = 1.seconds),
            colors = defaultJourneyColors(),
            onClose = onClose,
        )
    }
}
