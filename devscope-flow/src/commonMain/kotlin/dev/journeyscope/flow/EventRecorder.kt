package dev.journeyscope.flow

import dev.journeyscope.core.Journey
import dev.journeyscope.core.TraceContext
import dev.journeyscope.core.model.DiagnosticMetadata
import dev.journeyscope.core.model.SpanCategory
import dev.journeyscope.core.model.diagnosticMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet

internal class EventRecorder(
    private val ambient: TraceContext?,
    private val source: (() -> Journey?)?,
) {
    fun record(name: String, category: SpanCategory, metadata: DiagnosticMetadata) {
        try {
            if (ambient != null) {
                ambient.recordEvent(name, category, metadata)
            } else {
                source?.invoke()?.recordEvent(name, category, metadata)
            }
        } catch (ignored: Throwable) {
            return
        }
    }
}

internal object CollectorIds {
    private val counter = MutableStateFlow(0L)

    fun next(): String = "c${counter.updateAndGet { it + 1 }}"
}

internal fun <T> FlowTraceOptions<T>.summarize(value: T): String {
    val text = try {
        formatter.format(value)
    } catch (ignored: Throwable) {
        "<unformattable>"
    }
    return text.take(maxSummaryLength)
}

internal fun streamMetadata(
    stream: String,
    kind: String,
    event: String,
    detail: String,
    block: DiagnosticMetadata.Builder.() -> Unit = {},
): DiagnosticMetadata = diagnosticMetadata {
    put("state.stream", stream)
    put("flow.kind", kind)
    put("state.event", event)
    put("detail", detail)
    block()
}
