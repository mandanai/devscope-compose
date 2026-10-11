package dev.chayaphon.journeyscope.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chayaphon.journeyscope.ui.model.SequenceCheckUi
import dev.chayaphon.journeyscope.ui.theme.JourneyTheme

@Composable
internal fun ExpectedSequenceSection(
    sequences: List<SequenceCheckUi>,
    modifier: Modifier = Modifier,
) {
    val colors = JourneyTheme.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(color = colors.divider)
        sequences.forEach { sequence ->
            Text(
                "Expected sequence: ${sequence.name}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                sequence.steps.forEach { step ->
                    val mark = if (step.observed) "✓" else "○"
                    val note = if (step.observed) "observed" else "not observed (configured expectation)"
                    Text(
                        "$mark ${step.label} — $note",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (step.observed) colors.textSecondary else colors.textPrimary,
                    )
                }
            }
            if (sequence.missingCount > 0) {
                Text(
                    "Absence means instrumentation did not report the step, not that the app misbehaved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}
