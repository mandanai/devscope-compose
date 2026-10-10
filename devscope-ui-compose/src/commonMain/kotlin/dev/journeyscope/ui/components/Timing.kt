package dev.journeyscope.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.journeyscope.ui.model.TimingBreakdownUi
import dev.journeyscope.ui.theme.JourneyTheme

@Composable
internal fun JourneyTimingSection(
    timing: TimingBreakdownUi,
    modifier: Modifier = Modifier,
) {
    if (timing.rows.isEmpty()) return
    val colors = JourneyTheme.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Timing breakdown",
            style = MaterialTheme.typography.titleSmall,
            color = colors.textSecondary,
            modifier = Modifier.semantics { heading() },
        )
        timing.rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(row.category.label, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                Text(
                    "${row.attributedLabel} · ${row.percentLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                )
            }
        }
        Text("Total ${timing.totalLabel}", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        timing.slowestLabel?.let { Text("Slowest step: $it", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary) }
        timing.overlapNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.bottleneck) }
        Text(timing.criticalPathLabel, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
    }
}
