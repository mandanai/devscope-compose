package dev.chayaphon.journeyscope.flow

import dev.chayaphon.journeyscope.core.Journey
import dev.chayaphon.journeyscope.core.SpanHandle
import dev.chayaphon.journeyscope.core.TraceContext
import dev.chayaphon.journeyscope.core.model.DiagnosticMetadata
import dev.chayaphon.journeyscope.core.model.SpanCategory
import dev.chayaphon.journeyscope.core.model.SpanStatus
import dev.chayaphon.journeyscope.core.model.diagnosticMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Launches [block] under a span that records the job's completion, cancellation or failure.
 * Only boundaries launched through this function are traced; the journey is [journey] or the caller's ambient one.
 */
public fun CoroutineScope.launchTraced(
    name: String,
    journey: Journey? = null,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit,
): Job {
    val boundary = openBoundary(name, journey) ?: return launch(context, start, block)
    val job = launch(context + boundary.context, start, block)
    job.invokeOnCompletion(boundary::close)
    return job
}

/** [async] counterpart of [launchTraced]. */
public fun <T> CoroutineScope.asyncTraced(
    name: String,
    journey: Journey? = null,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> T,
): Deferred<T> {
    val boundary = openBoundary(name, journey) ?: return async(context, start, block)
    val deferred = async(context + boundary.context, start, block)
    deferred.invokeOnCompletion(boundary::close)
    return deferred
}

private class Boundary(
    val span: SpanHandle,
    val context: CoroutineContext,
) {
    fun close(cause: Throwable?) {
        when (cause) {
            null -> span.end(SpanStatus.OK, outcome("completed"))
            is CancellationException -> span.end(SpanStatus.CANCELLED, outcome("cancelled"))
            else -> span.fail(cause, outcome("failed"))
        }
    }

    private fun outcome(value: String): DiagnosticMetadata = diagnosticMetadata { put("coroutine.outcome", value) }
}

private fun CoroutineScope.openBoundary(
    name: String,
    explicit: Journey?,
): Boundary? {
    val ambient = coroutineContext[TraceContext]
    val journey = explicit ?: ambient?.journey ?: return null
    if (!journey.isRecording) return null
    val parent = ambient?.takeIf { it.journey === journey }
    val metadata =
        diagnosticMetadata {
            put("detail", "coroutine job created")
            put("coroutine.propagated", parent != null)
        }
    val span = parent?.beginChildSpan(name, SpanCategory.CUSTOM, metadata) ?: journey.beginSpan(name, SpanCategory.CUSTOM, metadata)
    if (!span.isRecording) return null
    return Boundary(span, journey.context(span))
}
