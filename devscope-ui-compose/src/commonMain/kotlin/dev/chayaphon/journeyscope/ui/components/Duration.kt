package dev.chayaphon.journeyscope.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.chayaphon.journeyscope.ui.model.DurationBreakdownUi
import dev.chayaphon.journeyscope.ui.model.DurationSegmentUi
import dev.chayaphon.journeyscope.ui.model.JourneyCategory
import dev.chayaphon.journeyscope.ui.theme.JourneyTheme

private const val MIN_SEGMENT_WEIGHT = 0.004f

@Composable
internal fun JourneyDurationSummary(
    breakdown: DurationBreakdownUi,
    onCategoryClick: ((JourneyCategory) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Total journey duration",
                style = MaterialTheme.typography.bodyMedium,
                color = JourneyTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = breakdown.totalLabel,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = JourneyTheme.colors.textPrimary,
                textAlign = TextAlign.End,
            )
        }
        JourneyDurationBreakdown(breakdown, onCategoryClick)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun JourneyDurationBreakdown(
    breakdown: DurationBreakdownUi,
    onCategoryClick: ((JourneyCategory) -> Unit)?,
    modifier: Modifier = Modifier,
    showLegend: Boolean = true,
) {
    val colors = JourneyTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(colors.divider)
                    .semantics { contentDescription = "Duration breakdown: ${breakdown.spokenDescription}" },
        ) {
            breakdown.segments.forEach { segment ->
                Box(
                    Modifier
                        .weight(segment.weight.coerceAtLeast(MIN_SEGMENT_WEIGHT))
                        .fillMaxHeight()
                        .background(colors.categoryColor(segment.category)),
                )
            }
        }
        if (showLegend) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                breakdown.segments.forEach { LegendItem(it, onCategoryClick) }
            }
        }
    }
}

@Composable
private fun LegendItem(
    segment: DurationSegmentUi,
    onClick: ((JourneyCategory) -> Unit)?,
) {
    val colors = JourneyTheme.colors
    val description = "${segment.category.label}, ${segment.label}, ${segment.percent} percent"
    val clickable =
        if (onClick != null) {
            Modifier.clip(RoundedCornerShape(8.dp)).clickable(
                onClickLabel = "Show slowest ${segment.category.label} step",
                role = Role.Button,
            ) { onClick(segment.category) }
        } else {
            Modifier
        }
    Row(
        modifier =
            clickable
                .padding(vertical = 6.dp)
                .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).background(colors.categoryColor(segment.category), CircleShape))
        Text(segment.category.label, style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
        Text(segment.label, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
    }
}
