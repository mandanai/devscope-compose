package dev.journeyscope.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.journeyscope.ui.components.JourneyDurationBreakdown
import dev.journeyscope.ui.components.JourneyEmptyState
import dev.journeyscope.ui.components.JourneyStatusBadge
import dev.journeyscope.ui.model.JourneyListItemUi
import dev.journeyscope.ui.theme.JourneyTheme

@Composable
internal fun JourneyListScreen(
    items: List<JourneyListItemUi>,
    selectedId: String?,
    onOpen: (String) -> Unit,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = JourneyTheme.colors
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    "Journeys",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Text("${items.size} recorded", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }
            if (onClose != null) TextButton(onClick = onClose) { Text("Close") }
        }
        if (items.isEmpty()) {
            JourneyEmptyState(
                title = "No journeys yet",
                message = "Journeys appear here as soon as the app records one.",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items, key = { it.id }, contentType = { "journey" }) { item ->
                    JourneyListItem(item, selected = item.id == selectedId, onClick = { onOpen(item.id) })
                }
            }
        }
    }
}

@Composable
internal fun JourneyListItem(item: JourneyListItemUi, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = JourneyTheme.colors
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClickLabel = "Open journey details", role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = item.spokenDescription },
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.running else colors.border),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.breakdown.totalLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    JourneyStatusBadge(item.status)
                }
            }
            JourneyDurationBreakdown(item.breakdown, onCategoryClick = null, showLegend = false)
        }
    }
}
