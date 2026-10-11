package dev.chayaphon.journeyscope.flow

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Release-build twins of the flow adapters: values pass through untouched and nothing is recorded. */
public fun <T> Flow<T>.traceFlow(
    name: String,
    options: FlowTraceOptions<T> = FlowTraceOptions(),
): Flow<T> = this

public fun <T> StateFlow<T>.traceState(
    name: String,
    options: FlowTraceOptions<T> = FlowTraceOptions(),
): StateFlow<T> = this

public fun <T> SharedFlow<T>.traceShared(
    name: String,
    options: FlowTraceOptions<T> = FlowTraceOptions(),
): SharedFlow<T> = this

public suspend fun <T> StateFlow<T>.observeWithJourneyScope(
    name: String,
    options: FlowTraceOptions<T> = FlowTraceOptions(),
    onEach: suspend (T) -> Unit = {},
): Nothing = collect { onEach(it) }
