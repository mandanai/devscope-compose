package dev.journeyscope.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.journeyscope.ui.model.JourneyCategory

internal sealed interface InspectorDestination {
    data object List : InspectorDestination
    data class Detail(val traceId: String) : InspectorDestination
}

private const val DETAIL_PREFIX = "detail:"

private val DestinationSaver = Saver<InspectorDestination, String>(
    save = { (it as? InspectorDestination.Detail)?.let { detail -> DETAIL_PREFIX + detail.traceId } ?: "" },
    restore = {
        if (it.startsWith(DETAIL_PREFIX)) InspectorDestination.Detail(it.removePrefix(DETAIL_PREFIX)) else InspectorDestination.List
    },
)

@Stable
internal class InspectorState(private val destinationState: MutableState<InspectorDestination>) {
    val destination: InspectorDestination get() = destinationState.value
    var filters: Set<JourneyCategory> by mutableStateOf(emptySet())
        private set
    var expandedRows: Set<String> by mutableStateOf(emptySet())
        private set
    var scrollTarget: String? by mutableStateOf(null)
        private set

    fun open(traceId: String) {
        destinationState.value = InspectorDestination.Detail(traceId)
        filters = emptySet()
        expandedRows = emptySet()
        scrollTarget = null
    }

    fun close() {
        destinationState.value = InspectorDestination.List
    }

    fun toggleFilter(category: JourneyCategory) {
        filters = if (category in filters) filters - category else filters + category
    }

    fun clearFilters() {
        filters = emptySet()
    }

    fun toggleExpanded(rowId: String) {
        expandedRows = if (rowId in expandedRows) expandedRows - rowId else expandedRows + rowId
    }

    fun expandAll(rowIds: Collection<String>) {
        expandedRows = rowIds.toSet()
    }

    fun collapseAll() {
        expandedRows = emptySet()
    }

    fun reveal(rowId: String) {
        filters = emptySet()
        expandedRows = expandedRows + rowId
        scrollTarget = rowId
    }

    fun consumeScrollTarget() {
        scrollTarget = null
    }
}

@Composable
internal fun rememberInspectorState(): InspectorState {
    val destination = rememberSaveable(stateSaver = DestinationSaver) { mutableStateOf<InspectorDestination>(InspectorDestination.List) }
    return remember(destination) { InspectorState(destination) }
}
