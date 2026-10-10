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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.journeyscope.ui.components.JourneyCategoryFilterRow
import dev.journeyscope.ui.components.ExpectedSequenceSection
import dev.journeyscope.ui.components.JourneyDiagnosticSection
import dev.journeyscope.ui.components.JourneyDurationSummary
import dev.journeyscope.ui.components.JourneyHeader
import dev.journeyscope.ui.components.journeyTimeline
import dev.journeyscope.ui.icons.GlyphIcon
import dev.journeyscope.ui.model.Glyph
import dev.journeyscope.ui.model.JourneyCategory
import dev.journeyscope.ui.model.JourneyDetailUi
import dev.journeyscope.ui.model.TimelineRowUi
import dev.journeyscope.ui.state.InspectorState
import dev.journeyscope.ui.theme.JourneyTheme

private const val LEADING_ITEM_COUNT = 3

@Composable
internal fun JourneyDetailScreen(
    detail: JourneyDetailUi,
    state: InspectorState,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = JourneyTheme.colors
    val listState = rememberLazyListState()
    val filters = state.filters
    val visibleRows = remember(detail.rows, filters) {
        if (filters.isEmpty()) detail.rows else detail.rows.filter { it.category in filters }
    }
    val availableCategories = remember(detail.rows) { detail.rows.map { it.category }.distinct() }
    val expandableIds = remember(visibleRows) { visibleRows.map { it.id } }

    val target = state.scrollTarget
    LaunchedEffect(target, visibleRows) {
        if (target == null) return@LaunchedEffect
        val index = visibleRows.indexOfFirst { it.id == target }
        if (index >= 0) {
            listState.animateScrollToItem(LEADING_ITEM_COUNT + index)
            state.consumeScrollTarget()
        }
    }

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onBack != null) BackBar(onBack)
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(28.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item(key = "header", contentType = "header") {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        JourneyHeader(detail.title, detail.subtitle, detail.status)
                        JourneyDurationSummary(detail.breakdown, onCategoryClick = { category -> revealSlowest(detail.rows, category, state) })
                    }
                }
                item(key = "timeline-title", contentType = "section") {
                    Text(
                        "Timeline",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }
                item(key = "filters", contentType = "filters") {
                    JourneyCategoryFilterRow(
                        available = availableCategories,
                        selected = filters,
                        allExpanded = expandableIds.isNotEmpty() && state.expandedRows.containsAll(expandableIds),
                        onToggleCategory = state::toggleFilter,
                        onClear = state::clearFilters,
                        onToggleExpandAll = {
                            if (state.expandedRows.containsAll(expandableIds)) state.collapseAll() else state.expandAll(expandableIds)
                        },
                    )
                }
                if (visibleRows.isEmpty()) {
                    item(key = "no-rows", contentType = "empty") {
                        Text(
                            if (detail.rows.isEmpty()) "No steps were recorded for this journey." else "No steps match the selected filters.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                } else {
                    journeyTimeline(visibleRows, state.expandedRows, state::toggleExpanded)
                }
                if (detail.sequences.isNotEmpty()) {
                    item(key = "sequences", contentType = "sequences") {
                        ExpectedSequenceSection(detail.sequences, modifier = Modifier.padding(top = 16.dp))
                    }
                }
                item(key = "insight", contentType = "insight") {
                    JourneyDiagnosticSection(detail.insights, onShowTarget = state::reveal, modifier = Modifier.padding(top = 16.dp))
                }
            }
        }
    }
}

private fun revealSlowest(rows: List<TimelineRowUi>, category: JourneyCategory, state: InspectorState) {
    rows.filter { it.category == category }.maxByOrNull { it.selfNanos }?.let { state.reveal(it.id) }
}

@Composable
private fun BackBar(onBack: () -> Unit) {
    val colors = JourneyTheme.colors
    Row(
        modifier = Modifier
            .clickable(onClickLabel = "Back to journeys", role = Role.Button, onClick = onBack)
            .padding(horizontal = 8.dp, vertical = 12.dp)
            .semantics { contentDescription = "Back to journeys" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlyphIcon(Glyph.Back, colors.textPrimary, Modifier.size(24.dp), size = 24.dp)
        Text("Journeys", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
    }
}
