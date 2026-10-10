package dev.journeyscope.core

import dev.journeyscope.core.model.DiagnosticMetadata
import dev.journeyscope.core.model.SpanCategory
import dev.journeyscope.core.model.SpanId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

/** Carries the active journey and span through suspension and dispatcher switches. */
public class TraceContext internal constructor(
    public val journey: Journey,
    public val spanId: SpanId?,
) : AbstractCoroutineContextElement(Key) {
    public companion object Key : CoroutineContext.Key<TraceContext>

    /** Records a point event under the span this context carries. */
    public fun recordEvent(
        name: String,
        category: SpanCategory = SpanCategory.CUSTOM,
        metadata: DiagnosticMetadata = DiagnosticMetadata.Empty,
    ) {
        safely { journey.recordEvent(name, category, metadata, spanId?.let(journey::handleFor)) }
    }

    /** Opens a span parented to the span this context carries; the caller must end it. */
    public fun beginChildSpan(
        name: String,
        category: SpanCategory = SpanCategory.CUSTOM,
        metadata: DiagnosticMetadata = DiagnosticMetadata.Empty,
    ): SpanHandle = journey.openSpan(name, category, metadata, spanId)
}

public suspend fun currentTraceContext(): TraceContext? = coroutineContext[TraceContext]

public fun CoroutineScope.launchInJourney(
    journey: Journey,
    parent: SpanHandle? = null,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit,
): Job = launch(context + journey.context(parent), start, block)
