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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.journeyscope.ui.icons.GlyphIcon
import dev.journeyscope.ui.model.Glyph
import dev.journeyscope.ui.model.InsightUi
import dev.journeyscope.ui.theme.JourneyTheme

@Composable
internal fun JourneyDiagnosticSection(
    insights: List<InsightUi>,
    onShowTarget: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (insights.isEmpty()) return
    val colors = JourneyTheme.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HorizontalDivider(color = colors.divider)
        insights.forEach { insight ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier.size(36.dp).background(colors.bottleneckContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(Glyph.Bulb, colors.bottleneck)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        insight.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(insight.message, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    insight.targetRowId?.let { target ->
                        TextButton(onClick = { onShowTarget(target) }, modifier = Modifier.padding(top = 2.dp)) {
                            Text("Show slowest step")
                        }
                    }
                }
            }
        }
    }
}
