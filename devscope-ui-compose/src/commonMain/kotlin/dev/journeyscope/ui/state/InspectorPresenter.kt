package dev.journeyscope.ui.state

import dev.journeyscope.ui.mapping.TraceMapper
import dev.journeyscope.ui.model.JourneyDetailUi
import dev.journeyscope.ui.model.JourneyListItemUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

internal sealed interface ListState {
    data object Loading : ListState
    data class Ready(val items: List<JourneyListItemUi>) : ListState
    data class Error(val reason: String) : ListState
}

internal sealed interface DetailState {
    data object Loading : DetailState
    data class Ready(val detail: JourneyDetailUi) : DetailState
    data object Missing : DetailState
    data class Error(val reason: String) : DetailState
}

internal class InspectorPresenter(
    private val source: JourneyTraceSource,
    private val mapper: TraceMapper,
) {
    fun list(): Flow<ListState> = source.observe()
        .map<TraceBatch, ListState> { batch ->
            ListState.Ready(batch.traces.asReversed().map { mapper.listItem(it, batch.nowNanos) })
        }
        .distinctUntilChanged()
        .catch { if (it is CancellationException) throw it else emit(ListState.Error(it::class.simpleName ?: "Error")) }
        .flowOn(Dispatchers.Default)

    fun detail(id: String): Flow<DetailState> = source.observe()
        .map<TraceBatch, DetailState> { batch ->
            batch.traces.firstOrNull { it.id.value == id }
                ?.let { DetailState.Ready(mapper.detail(it, batch.nowNanos)) }
                ?: DetailState.Missing
        }
        .distinctUntilChanged()
        .catch { if (it is CancellationException) throw it else emit(DetailState.Error(it::class.simpleName ?: "Error")) }
        .flowOn(Dispatchers.Default)
}
