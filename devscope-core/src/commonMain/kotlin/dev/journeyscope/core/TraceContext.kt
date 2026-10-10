package dev.journeyscope.core

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
}

public suspend fun currentTraceContext(): TraceContext? = coroutineContext[TraceContext]

public fun CoroutineScope.launchInJourney(
    journey: Journey,
    parent: SpanHandle? = null,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit,
): Job = launch(context + journey.context(parent), start, block)
