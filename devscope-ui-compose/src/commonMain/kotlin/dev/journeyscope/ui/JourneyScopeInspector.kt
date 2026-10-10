package dev.journeyscope.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.journeyscope.core.JourneyScope
import dev.journeyscope.core.expectation.ExpectedSequence
import dev.journeyscope.ui.mapping.DefaultInsightProvider
import dev.journeyscope.ui.mapping.InspectorThresholds
import dev.journeyscope.ui.mapping.JourneyInsightProvider
import dev.journeyscope.ui.mapping.MetadataDisplayPolicy
import dev.journeyscope.ui.mapping.TraceMapper
import dev.journeyscope.ui.screen.JourneyInspectorScreen
import dev.journeyscope.ui.state.InspectorPresenter
import dev.journeyscope.ui.state.JourneyTraceSource
import dev.journeyscope.ui.state.asTraceSource
import dev.journeyscope.ui.theme.JourneyColors
import dev.journeyscope.ui.theme.JourneyScopeTheme
import dev.journeyscope.ui.theme.defaultJourneyColors

@Composable
public fun JourneyScopeInspector(
    modifier: Modifier = Modifier,
    source: JourneyTraceSource = rememberRecorderSource(),
    thresholds: InspectorThresholds = rememberRecorderThresholds(),
    insightProvider: JourneyInsightProvider = DefaultInsightProvider(),
    metadataPolicy: MetadataDisplayPolicy = MetadataDisplayPolicy.Default,
    colors: JourneyColors = defaultJourneyColors(),
    expectedSequences: List<ExpectedSequence> = emptyList(),
    onClose: (() -> Unit)? = null,
) {
    val presenter = remember(source, thresholds, insightProvider, metadataPolicy, expectedSequences) {
        InspectorPresenter(source, TraceMapper(thresholds, metadataPolicy, insightProvider, expectedSequences))
    }
    JourneyScopeTheme(colors = colors) {
        JourneyInspectorScreen(presenter = presenter, onClose = onClose, modifier = modifier)
    }
}

@Composable
private fun rememberRecorderSource(): JourneyTraceSource {
    val recorder = JourneyScope.tracer.recorder
    return remember(recorder) { recorder.asTraceSource() }
}

@Composable
private fun rememberRecorderThresholds(): InspectorThresholds {
    val config = JourneyScope.tracer.config
    return remember(config) { InspectorThresholds.from(config) }
}
