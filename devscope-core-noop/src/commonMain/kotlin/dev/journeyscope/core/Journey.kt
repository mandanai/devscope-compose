package dev.journeyscope.core

import dev.journeyscope.core.model.DiagnosticMetadata
import dev.journeyscope.core.model.SpanCategory
import dev.journeyscope.core.model.SpanId
import dev.journeyscope.core.model.SpanStatus
import dev.journeyscope.core.model.TraceId
import dev.journeyscope.core.model.TraceStatus
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

public class Journey internal constructor() {
    public val id: TraceId = TraceId("noop")
    public val name: String = "noop"
    public val status: TraceStatus = TraceStatus.CREATED
    public val isRecording: Boolean = false

    public fun start() {}

    public fun recordEvent(
        name: String,
        category: SpanCategory = SpanCategory.CUSTOM,
        metadata: DiagnosticMetadata = DiagnosticMetadata.Empty,
        span: SpanHandle? = null,
    ) {}

    public fun beginSpan(
        name: String,
        category: SpanCategory = SpanCategory.CUSTOM,
        metadata: DiagnosticMetadata = DiagnosticMetadata.Empty,
        parent: SpanHandle? = null,
    ): SpanHandle = SpanHandle.INERT

    public fun context(parent: SpanHandle? = null): CoroutineContext = EmptyCoroutineContext

    public fun finish() {}

    public fun fail(throwable: Throwable) {}

    public fun cancel(reason: String? = null) {}

    public fun interrupt(reason: String) {}

    public fun <T> trace(
        name: String,
        category: SpanCategory = SpanCategory.CUSTOM,
        metadata: DiagnosticMetadata = DiagnosticMetadata.Empty,
        parent: SpanHandle? = null,
        block: (SpanHandle) -> T,
    ): T = block(SpanHandle.INERT)

    public suspend fun <T> traceSuspend(
        name: String,
        category: SpanCategory = SpanCategory.CUSTOM,
        metadata: DiagnosticMetadata = DiagnosticMetadata.Empty,
        block: suspend () -> T,
    ): T = block()

    internal companion object {
        val INERT: Journey = Journey()
    }
}

public class SpanHandle internal constructor() {
    public val id: SpanId = SpanId("noop")
    public val isRecording: Boolean = false

    public fun end(status: SpanStatus = SpanStatus.OK, metadata: DiagnosticMetadata = DiagnosticMetadata.Empty) {}

    public fun fail(throwable: Throwable, metadata: DiagnosticMetadata = DiagnosticMetadata.Empty) {}

    public fun recordEvent(
        name: String,
        category: SpanCategory = SpanCategory.CUSTOM,
        metadata: DiagnosticMetadata = DiagnosticMetadata.Empty,
    ) {}

    internal companion object {
        val INERT: SpanHandle = SpanHandle()
    }
}
