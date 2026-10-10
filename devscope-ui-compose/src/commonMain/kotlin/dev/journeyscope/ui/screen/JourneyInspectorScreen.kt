package dev.journeyscope.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.journeyscope.ui.components.JourneyEmptyState
import dev.journeyscope.ui.components.JourneyErrorState
import dev.journeyscope.ui.components.JourneyLoadingState
import dev.journeyscope.ui.state.DetailState
import dev.journeyscope.ui.state.InspectorDestination
import dev.journeyscope.ui.state.InspectorPresenter
import dev.journeyscope.ui.state.ListState
import dev.journeyscope.ui.state.rememberInspectorState
import dev.journeyscope.ui.theme.JourneyTheme

private val TwoPaneBreakpoint = 600.dp
private val ListPaneWidth = 340.dp
private val ContentMaxWidth = 760.dp

@Composable
internal fun JourneyInspectorScreen(
    presenter: InspectorPresenter,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val state = rememberInspectorState()
    var retryKey by remember { mutableIntStateOf(0) }
    val listState by remember(presenter, retryKey) { presenter.list() }.collectAsState(ListState.Loading)
    val destination = state.destination

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(JourneyTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        val twoPane = maxWidth >= TwoPaneBreakpoint
        NavigationBackHandler(
            state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
            isBackEnabled = !twoPane && destination is InspectorDestination.Detail,
            onBackCompleted = state::close,
        )

        val body: @Composable (Modifier) -> Unit = { bodyModifier ->
            when (val current = listState) {
                ListState.Loading -> JourneyLoadingState(bodyModifier)
                is ListState.Error -> JourneyErrorState(current.reason, onRetry = { retryKey++ }, modifier = bodyModifier)
                is ListState.Ready -> {
                    val selectedId = (destination as? InspectorDestination.Detail)?.traceId
                    if (twoPane) {
                        Row(bodyModifier) {
                            JourneyListScreen(
                                items = current.items,
                                selectedId = selectedId,
                                onOpen = state::open,
                                onClose = onClose,
                                modifier = Modifier.width(ListPaneWidth).fillMaxHeight(),
                            )
                            Box(Modifier.weight(1f).fillMaxHeight().padding(start = 16.dp)) {
                                if (selectedId == null) {
                                    JourneyEmptyState("Select a journey", "Pick a journey on the left to inspect its timeline.")
                                } else {
                                    DetailPane(presenter, selectedId, state, onBack = null, retry = { retryKey++ })
                                }
                            }
                        }
                    } else if (selectedId != null) {
                        DetailPane(presenter, selectedId, state, onBack = state::close, retry = { retryKey++ }, modifier = bodyModifier)
                    } else {
                        JourneyListScreen(current.items, selectedId = null, onOpen = state::open, onClose = onClose, modifier = bodyModifier)
                    }
                }
            }
        }
        body(Modifier.widthIn(max = if (twoPane) Dp.Unspecified else ContentMaxWidth).padding(16.dp))
    }
}

@Composable
private fun DetailPane(
    presenter: InspectorPresenter,
    traceId: String,
    state: dev.journeyscope.ui.state.InspectorState,
    onBack: (() -> Unit)?,
    retry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detailState by remember(presenter, traceId) { presenter.detail(traceId) }.collectAsState(DetailState.Loading)
    when (val current = detailState) {
        DetailState.Loading -> JourneyLoadingState(modifier)
        DetailState.Missing -> JourneyEmptyState("Journey not found", "It may have been evicted from the recorder.", modifier)
        is DetailState.Error -> JourneyErrorState(current.reason, onRetry = retry, modifier = modifier)
        is DetailState.Ready -> JourneyDetailScreen(current.detail, state, onBack, modifier)
    }
}
