package dev.chayaphon.journeyscope.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.chayaphon.journeyscope.core.JourneyScope
import dev.chayaphon.journeyscope.core.expectation.ExpectedSequence
import dev.chayaphon.journeyscope.diagnostics.DiagnosticAnalyzer
import dev.chayaphon.journeyscope.diagnostics.DiagnosticConfig
import dev.chayaphon.journeyscope.export.JourneyExportController
import dev.chayaphon.journeyscope.ui.mapping.InspectorThresholds
import dev.chayaphon.journeyscope.ui.mapping.MetadataDisplayPolicy
import dev.chayaphon.journeyscope.ui.mapping.TraceMapper
import dev.chayaphon.journeyscope.ui.screen.JourneyInspectorScreen
import dev.chayaphon.journeyscope.ui.state.InspectorPresenter
import dev.chayaphon.journeyscope.ui.state.JourneyTraceSource
import dev.chayaphon.journeyscope.ui.state.asTraceSource
import dev.chayaphon.journeyscope.ui.state.withExport
import dev.chayaphon.journeyscope.ui.theme.JourneyColors
import dev.chayaphon.journeyscope.ui.theme.JourneyScopeTheme
import dev.chayaphon.journeyscope.ui.theme.defaultJourneyColors

/** Self-contained, read-only trace inspector that renders on-device only. Defaults to the global [JourneyScope] recorder; pass [exportController] to enable history, import and sharing. */
@Composable
public fun JourneyScopeInspector(
    modifier: Modifier = Modifier,
    source: JourneyTraceSource = rememberRecorderSource(),
    thresholds: InspectorThresholds = rememberRecorderThresholds(),
    analyzer: DiagnosticAnalyzer? = null,
    metadataPolicy: MetadataDisplayPolicy = MetadataDisplayPolicy.Default,
    colors: JourneyColors = defaultJourneyColors(),
    expectedSequences: List<ExpectedSequence> = emptyList(),
    onClose: (() -> Unit)? = null,
    exportController: JourneyExportController? = null,
) {
    val presenter =
        remember(source, thresholds, analyzer, metadataPolicy, expectedSequences, exportController) {
            val resolved =
                analyzer ?: DiagnosticAnalyzer(
                    DiagnosticConfig(slowJourney = thresholds.slowJourney, expectedSequences = expectedSequences),
                )
            InspectorPresenter(
                exportController?.let {
                    source.withExport(it)
                } ?: source,
                TraceMapper(thresholds, metadataPolicy, resolved, expectedSequences),
            )
        }
    JourneyScopeTheme(colors = colors) {
        JourneyInspectorScreen(presenter = presenter, onClose = onClose, modifier = modifier, exportController = exportController)
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
