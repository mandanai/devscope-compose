package dev.journeyscope.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.journeyscope.ui.icons.GlyphIcon
import dev.journeyscope.ui.model.Glyph
import dev.journeyscope.ui.model.RowState
import dev.journeyscope.ui.model.TimelineRowUi
import dev.journeyscope.ui.theme.JourneyTheme

private const val INDENT_STEP_DP = 14

internal fun LazyListScope.journeyTimeline(
    rows: List<TimelineRowUi>,
    expandedIds: Set<String>,
    onToggle: (String) -> Unit,
) {
    items(items = rows, key = { it.id }, contentType = { "timeline-row" }) { row ->
        JourneyTimelineItem(row, expanded = row.id in expandedIds, onToggle = { onToggle(row.id) })
    }
}

@Composable
internal fun JourneyTimelineItem(
    row: TimelineRowUi,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = JourneyTheme.colors
    val accent = colors.categoryColor(row.category)
    val background = if (row.isBottleneck) colors.bottleneckContainer else Color.Transparent
    val durationColor =
        when {
            row.state == RowState.Failed -> colors.failure
            row.isBottleneck -> colors.bottleneck
            else -> colors.textPrimary
        }
    val spoken = if (expanded) row.spokenDescription + ". " + expandedSpoken(row) else row.spokenDescription
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(background)
                .clickable(onClickLabel = if (expanded) "Collapse details" else "Expand details", role = Role.Button, onClick = onToggle)
                .padding(start = (12 + row.depth * INDENT_STEP_DP).dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
                .clearAndSetSemantics {
                    contentDescription = spoken
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                    role = Role.Button
                    onClick(label = if (expanded) "Collapse details" else "Expand details") {
                        onToggle()
                        true
                    }
                },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(36.dp).background(accent.copy(alpha = ICON_BACKGROUND_ALPHA), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(row.glyph, accent)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                Text(row.detail, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = row.durationLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (row.isBottleneck) FontWeight.Bold else FontWeight.SemiBold,
                    color = durationColor,
                    textAlign = TextAlign.End,
                )
                stateLabel(row.state)?.let { (label, color) ->
                    Text(label, style = MaterialTheme.typography.labelSmall, color = color(colors))
                }
            }
            GlyphIcon(Glyph.ChevronDown, colors.textSecondary, Modifier.rotate(if (expanded) 180f else 0f), size = 16.dp)
        }
        if (expanded) ExpandedDetails(row)
    }
}

private const val ICON_BACKGROUND_ALPHA = 0.14f

private fun stateLabel(state: RowState): Pair<String, (dev.journeyscope.ui.theme.JourneyColors) -> Color>? =
    when (state) {
        RowState.Running -> "Running" to { c -> c.running }
        RowState.Failed -> "Failed" to { c -> c.failure }
        RowState.Cancelled -> "Cancelled" to { c -> c.textSecondary }
        RowState.Interrupted -> "Interrupted" to { c -> c.textSecondary }
        RowState.Ok, RowState.Event -> null
    }

@Composable
private fun ExpandedDetails(row: TimelineRowUi) {
    val colors = JourneyTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 48.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DetailLine("Position", row.offsetLabel)
        row.selfLabel?.let { DetailLine("Own time", it) }
        row.failureLabel?.let { DetailLine("Failure", it, valueColor = colors.failure) }
        row.metadata.forEach { DetailLine(it.key, it.value, valueColor = if (it.hidden) colors.textSecondary else colors.textPrimary) }
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
    valueColor: Color = JourneyTheme.colors.textPrimary,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = JourneyTheme.colors.textSecondary,
            modifier = Modifier.weight(0.4f),
        )
        Text(value, style = MaterialTheme.typography.labelMedium, color = valueColor, modifier = Modifier.weight(0.6f))
    }
}

private fun expandedSpoken(row: TimelineRowUi): String =
    buildList {
        add(row.offsetLabel)
        row.selfLabel?.let { add("own time $it") }
        row.failureLabel?.let { add("failure $it") }
        row.metadata.forEach { add("${it.key} ${it.value}") }
    }.joinToString(", ")
