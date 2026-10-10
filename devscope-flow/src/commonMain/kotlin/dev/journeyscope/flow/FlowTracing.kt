package dev.journeyscope.flow

import dev.journeyscope.core.currentTraceContext
import dev.journeyscope.core.model.SpanCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/**
 * Records collection and value events at this point in the chain without changing values or order.
 * Values are reported only when they reach this operator; nothing upstream of it is claimed.
 */
public fun <T> Flow<T>.traceFlow(
    name: String,
    options: FlowTraceOptions<T> = FlowTraceOptions(),
): Flow<T> {
    val upstream = this
    return flow {
        val recorder = EventRecorder(currentTraceContext(), options.journey)
        val collectorId = CollectorIds.next()
        recorder.record(
            "$name collection started",
            SpanCategory.STATE,
            streamMetadata(name, "flow", "started", "collector $collectorId") { put("collector.id", collectorId) },
        )
        try {
            upstream.collect { value ->
                if (options.recordUpstream) {
                    recorder.record(
                        "$name value emitted upstream",
                        SpanCategory.STATE,
                        streamMetadata(name, "flow", "upstream", options.summarize(value)) { put("collector.id", collectorId) },
                    )
                }
                emit(value)
                if (options.recordObserved) {
                    recorder.record(
                        "$name value observed",
                        SpanCategory.STATE,
                        streamMetadata(name, "flow", "observed", options.summarize(value)) { put("collector.id", collectorId) },
                    )
                }
            }
            recorder.record(
                "$name collection completed",
                SpanCategory.STATE,
                streamMetadata(name, "flow", "completed", "collector $collectorId") { put("collector.id", collectorId) },
            )
        } catch (e: Throwable) {
            val cancelled = e is CancellationException
            recorder.record(
                if (cancelled) "$name collector cancelled" else "$name collection failed",
                SpanCategory.STATE,
                streamMetadata(name, "flow", if (cancelled) "cancelled" else "failed", "collector $collectorId") {
                    put("collector.id", collectorId)
                    if (!cancelled) put("state.failure", e::class.simpleName ?: "Throwable")
                },
            )
            throw e
        }
    }
}

/**
 * Wraps a [StateFlow] so each collector's lifetime and observed transitions are recorded.
 * [StateFlow] conflates equal values before this wrapper sees them; only values delivered to a collector are reported.
 */
public fun <T> StateFlow<T>.traceState(
    name: String,
    options: FlowTraceOptions<T> = FlowTraceOptions(),
): StateFlow<T> = TracedStateFlow(this, name, options)

/** Wraps a [SharedFlow] so collector lifetime and delivered values are recorded. */
public fun <T> SharedFlow<T>.traceShared(
    name: String,
    options: FlowTraceOptions<T> = FlowTraceOptions(),
): SharedFlow<T> = TracedSharedFlow(this, name, options)

/** Collects [this] through [traceState]; the typical UI-side observation point. */
public suspend fun <T> StateFlow<T>.observeWithJourneyScope(
    name: String,
    options: FlowTraceOptions<T> = FlowTraceOptions(),
    onEach: suspend (T) -> Unit = {},
): Nothing = traceState(name, options).collect(FlowCollector { onEach(it) })

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class TracedStateFlow<T>(
    private val delegate: StateFlow<T>,
    private val name: String,
    private val options: FlowTraceOptions<T>,
) : StateFlow<T> {
    private val collectors = MutableStateFlow(0)

    override val value: T get() = delegate.value

    override val replayCache: List<T> get() = delegate.replayCache

    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        collectTraced(delegate, name, "state", options, collectors, collector)
        error("StateFlow collection never completes")
    }
}

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class TracedSharedFlow<T>(
    private val delegate: SharedFlow<T>,
    private val name: String,
    private val options: FlowTraceOptions<T>,
) : SharedFlow<T> {
    private val collectors = MutableStateFlow(0)

    override val replayCache: List<T> get() = delegate.replayCache

    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        collectTraced(delegate, name, "shared", options, collectors, collector)
        error("SharedFlow collection never completes")
    }
}

private suspend fun <T> collectTraced(
    delegate: Flow<T>,
    name: String,
    kind: String,
    options: FlowTraceOptions<T>,
    collectors: MutableStateFlow<Int>,
    collector: FlowCollector<T>,
) {
    val recorder = EventRecorder(currentTraceContext(), options.journey)
    val collectorId = CollectorIds.next()
    val active = collectors.updateAndGet { it + 1 }
    recorder.record(
        "$name collector added",
        SpanCategory.STATE,
        streamMetadata(name, kind, "collector-added", "collector $collectorId · $active active") {
            put("collector.id", collectorId)
            put("state.collectors", active)
        },
    )
    var previous: String? = null
    var end = "completed"
    var failure: String? = null
    try {
        coroutineScope {
            options.lifecycle?.let { source ->
                launch { source.states().collect { recorder.recordLifecycle(name, collectorId, it) } }
            }
            delegate.collect { value ->
                if (options.recordObserved) {
                    val current = options.summarize(value)
                    recorder.record(
                        "$name ${if (kind == "state") "state" else "value"} observed",
                        SpanCategory.STATE,
                        streamMetadata(name, kind, "observed", previous?.let { "$it → $current" } ?: "→ $current") {
                            put("collector.id", collectorId)
                            put("state.current", current)
                            previous?.let { put("state.previous", it) }
                            put("state.changed", previous != current)
                        },
                    )
                    previous = current
                }
                collector.emit(value)
            }
        }
    } catch (e: Throwable) {
        if (e is CancellationException) {
            end = "cancelled"
        } else {
            end = "failed"
            failure = e::class.simpleName ?: "Throwable"
        }
        throw e
    } finally {
        val remaining = collectors.updateAndGet { it - 1 }
        recorder.record(
            "$name collector removed",
            SpanCategory.STATE,
            streamMetadata(name, kind, "collector-removed", "collector $collectorId · $end · $remaining active") {
                put("collector.id", collectorId)
                put("state.collectors", remaining)
                put("state.end", end)
                failure?.let { put("state.failure", it) }
            },
        )
    }
}

private fun EventRecorder.recordLifecycle(
    name: String,
    collectorId: String,
    state: CollectorLifecycleState,
) {
    record(
        "$name lifecycle ${state.name.lowercase()}",
        SpanCategory.LIFECYCLE,
        streamMetadata(name, "lifecycle", "lifecycle", "collector $collectorId · ${state.name}") {
            put("collector.id", collectorId)
            put("lifecycle.state", state.name)
        },
    )
}
