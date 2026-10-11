package dev.chayaphon.journeyscope.core

import dev.chayaphon.journeyscope.core.model.DiagnosticMetadata
import dev.chayaphon.journeyscope.core.model.SpanCategory
import dev.chayaphon.journeyscope.core.model.SpanId
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Release-build twin of the real trace context; never bound to a coroutine, so lookups return null. */
public class TraceContext internal constructor(
    public val journey: Journey,
    public val spanId: SpanId?,
) : AbstractCoroutineContextElement(Key) {
    public companion object Key : CoroutineContext.Key<TraceContext>

    public fun recordEvent(
        name: String,
        category: SpanCategory = SpanCategory.CUSTOM,
        metadata: DiagnosticMetadata = DiagnosticMetadata.Empty,
    ) {}

    public fun beginChildSpan(
        name: String,
        category: SpanCategory = SpanCategory.CUSTOM,
        metadata: DiagnosticMetadata = DiagnosticMetadata.Empty,
    ): SpanHandle = SpanHandle.INERT
}

public suspend fun currentTraceContext(): TraceContext? = null
