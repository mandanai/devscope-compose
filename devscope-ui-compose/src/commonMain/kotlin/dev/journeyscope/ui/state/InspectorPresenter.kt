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
            ListState.Ready(
                batch.traces.asReversed().map { mapper.listItem(it, batch.nowNanos).copy(imported = it.id.value in batch.importedIds) },
            )
        }
        .distinctUntilChanged()
        .catch { if (it is CancellationException) throw it else emit(ListState.Error(it::class.simpleName ?: "Error")) }
        .flowOn(Dispatchers.Default)

    fun detail(id: String): Flow<DetailState> = source.observe()
        .map<TraceBatch, DetailState> { batch ->
            batch.traces.firstOrNull { it.id.value == id }
                ?.let { trace ->
                    val detail = mapper.detail(trace, batch.nowNanos)
                    DetailState.Ready(if (trace.id.value in batch.importedIds) detail.copy(subtitle = "Imported · " + detail.subtitle) else detail)
                }
                ?: DetailState.Missing
        }
        .distinctUntilChanged()
        .catch { if (it is CancellationException) throw it else emit(DetailState.Error(it::class.simpleName ?: "Error")) }
        .flowOn(Dispatchers.Default)
}
