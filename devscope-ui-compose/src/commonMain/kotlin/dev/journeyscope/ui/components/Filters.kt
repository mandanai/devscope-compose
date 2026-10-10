package dev.journeyscope.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.journeyscope.ui.model.JourneyCategory

@Composable
internal fun JourneyCategoryFilterRow(
    available: List<JourneyCategory>,
    selected: Set<JourneyCategory>,
    allExpanded: Boolean,
    onToggleCategory: (JourneyCategory) -> Unit,
    onClear: () -> Unit,
    onToggleExpandAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "all") {
                FilterChip(selected = selected.isEmpty(), onClick = onClear, label = { Text("All", style = MaterialTheme.typography.labelLarge) })
            }
            items(available, key = { it.name }) { category ->
                FilterChip(
                    selected = category in selected,
                    onClick = { onToggleCategory(category) },
                    label = { Text(category.label, style = MaterialTheme.typography.labelLarge) },
                )
            }
        }
        TextButton(onClick = onToggleExpandAll) { Text(if (allExpanded) "Collapse all" else "Expand all") }
    }
}
