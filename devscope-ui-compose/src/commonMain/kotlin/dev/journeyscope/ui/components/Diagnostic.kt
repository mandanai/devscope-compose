package dev.journeyscope.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.journeyscope.diagnostics.DiagnosticSeverity
import dev.journeyscope.ui.icons.GlyphIcon
import dev.journeyscope.ui.model.DiagnosticUi
import dev.journeyscope.ui.model.Glyph
import dev.journeyscope.ui.theme.JourneyTheme

@Composable
internal fun JourneyDiagnosticSection(
    diagnostics: List<DiagnosticUi>,
    onShowTarget: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (diagnostics.isEmpty()) return
    val colors = JourneyTheme.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HorizontalDivider(color = colors.divider)
        Text(
            "Diagnostics",
            style = MaterialTheme.typography.titleSmall,
            color = colors.textSecondary,
            modifier = Modifier.semantics { heading() },
        )
        diagnostics.forEach { DiagnosticItem(it, onShowTarget) }
    }
}

@Composable
private fun DiagnosticItem(diagnostic: DiagnosticUi, onShowTarget: (String) -> Unit) {
    val colors = JourneyTheme.colors
    val (accent, container) = severityColors(diagnostic.severity)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(36.dp).background(container, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.Bulb, accent)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${diagnostic.severity.name.uppercase()} · ${diagnostic.categoryLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
            )
            Text(
                diagnostic.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            LabelledLines("Evidence", diagnostic.evidence)
            diagnostic.inference?.let { LabelledLines("Inference", listOf(it)) }
            LabelledLines("Suggested investigation", diagnostic.nextSteps)
            if (diagnostic.metadata.isNotEmpty()) {
                Text(
                    diagnostic.metadata.joinToString(" · ") { "${it.key}: ${it.value}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            diagnostic.related.forEach { related ->
                TextButton(onClick = { onShowTarget(related.rowId) }, modifier = Modifier.padding(top = 2.dp)) {
                    Text("Show ${related.label}")
                }
            }
        }
    }
}

@Composable
private fun LabelledLines(label: String, lines: List<String>) {
    if (lines.isEmpty()) return
    val colors = JourneyTheme.colors
    Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary)
    lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary) }
}

@Composable
private fun severityColors(severity: DiagnosticSeverity): Pair<Color, Color> {
    val colors = JourneyTheme.colors
    return when (severity) {
        DiagnosticSeverity.Error -> colors.failure to colors.failureContainer
        DiagnosticSeverity.Warning -> colors.bottleneck to colors.bottleneckContainer
        DiagnosticSeverity.Info -> colors.running to colors.runningContainer
    }
}
