package dev.journeyscope.core

import dev.journeyscope.core.model.DiagnosticMetadata
import dev.journeyscope.core.model.JourneyTrace
import dev.journeyscope.core.model.SpanCategory
import dev.journeyscope.core.model.SpanId
import dev.journeyscope.core.model.SpanStatus
import dev.journeyscope.core.model.TraceId
import dev.journeyscope.core.model.TraceStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Live handle for one end-to-end interaction. Disabled or unsampled journeys are inert:
 * every call is a no-op and blocks still run and return their own results.
 */
public abstract class Journey internal constructor() {
    public abstract val id: TraceId
    public abstract val name: String
    public abstract val status: TraceStatus

    /** False for inert journeys that record nothing. */
    public abstract val isRecording: Boolean

    /** Null for inert journeys. */
    public abstract fun snapshot(): JourneyTrace?

    public abstract fun start()

    public abstract fun recordEvent(
        name: String,
        category: SpanCategory = SpanCategory.CUSTOM,
        metadata: DiagnosticMetadata = DiagnosticMetadata.Empty,
        span: SpanHandle? = null,
    )

    internal abstract fun openSpan(
        name: String,
        category: SpanCategory,
        metadata: DiagnosticMetadata,
        parentId: SpanId?,
    ): SpanHandle

    /** For callback-style work that cannot use [trace] or [traceSuspend]; the caller must end the span. */
    public fun beginSpan(
        name: String,
        category: SpanCategory = SpanCategory.CUSTOM,
        metadata: DiagnosticMetadata = DiagnosticMetadata.Empty,
        parent: SpanHandle? = null,
    ): SpanHandle = openSpan(name, category, metadata, parent?.takeIf { it.isRecording }?.id)

    /** Binds this journey (and optionally [parent]) to manually launched coroutines. */
    public abstract fun context(parent: SpanHandle? = null): CoroutineContext

    public abstract fun finish()

    public abstract fun fail(throwable: Throwable)

    public abstract fun cancel(reason: String? = null)

    public abstract fun interrupt(reason: String)

    /** Synchronous code cannot see a coroutine context, so nesting is explicit via [parent] or the passed handle. */
    public fun <T> trace(
        name: String,
        category: SpanCategory = SpanCategory.CUSTOM,
        metadata: DiagnosticMetadata = DiagnosticMetadata.Empty,
        parent: SpanHandle? = null,
        block: (SpanHandle) -> T,
    ): T {
        val span = beginSpan(name, category, metadata, parent)
        var thrown: Throwable? = null
        try {
            return block(span)
        } catch (t: Throwable) {
            thrown = t
            throw t
        } finally {
            span.endFrom(thrown)
        }
    }

    public suspend fun <T> traceSuspend(
        name: String,
        category: SpanCategory = SpanCategory.CUSTOM,
        metadata: DiagnosticMetadata = DiagnosticMetadata.Empty,
        block: suspend () -> T,
    ): T {
        if (!isRecording) return block()
        val parentId = coroutineContext[TraceContext]?.takeIf { it.journey === this }?.spanId
        val span = openSpan(name, category, metadata, parentId)
        val contextSpanId = if (span.isRecording) span.id else parentId
        var thrown: Throwable? = null
        try {
            return withContext(TraceContext(this, contextSpanId)) { block() }
        } catch (t: Throwable) {
            thrown = t
            throw t
        } finally {
            span.endFrom(thrown)
        }
    }
}

public abstract class SpanHandle internal constructor() {
    public abstract val id: SpanId
    public abstract val isRecording: Boolean

    /** The first call wins; later calls are ignored. [metadata] is merged into the span's start metadata. */
    public abstract fun end(status: SpanStatus = SpanStatus.OK, metadata: DiagnosticMetadata = DiagnosticMetadata.Empty)

    public abstract fun fail(throwable: Throwable, metadata: DiagnosticMetadata = DiagnosticMetadata.Empty)

    public abstract fun recordEvent(
        name: String,
        category: SpanCategory = SpanCategory.CUSTOM,
        metadata: DiagnosticMetadata = DiagnosticMetadata.Empty,
    )

    internal fun endFrom(throwable: Throwable?) {
        when (throwable) {
            null -> end(SpanStatus.OK)
            is CancellationException -> end(SpanStatus.CANCELLED)
            else -> fail(throwable)
        }
    }
}

internal inline fun safely(block: () -> Unit) {
    try {
        block()
    } catch (ignored: Throwable) {
        // SDK faults must never surface in, or replace errors from, application code.
    }
}

internal object NoopSpanHandle : SpanHandle() {
    override val id: SpanId = SpanId("noop")
    override val isRecording: Boolean = false

    override fun end(status: SpanStatus, metadata: DiagnosticMetadata) = Unit

    override fun fail(throwable: Throwable, metadata: DiagnosticMetadata) = Unit

    override fun recordEvent(name: String, category: SpanCategory, metadata: DiagnosticMetadata) = Unit
}

internal object NoopJourney : Journey() {
    override val id: TraceId = TraceId("noop")
    override val name: String = "noop"
    override val status: TraceStatus = TraceStatus.CREATED
    override val isRecording: Boolean = false

    override fun snapshot(): JourneyTrace? = null

    override fun start() = Unit

    override fun recordEvent(name: String, category: SpanCategory, metadata: DiagnosticMetadata, span: SpanHandle?) = Unit

    override fun openSpan(name: String, category: SpanCategory, metadata: DiagnosticMetadata, parentId: SpanId?): SpanHandle =
        NoopSpanHandle

    override fun context(parent: SpanHandle?): CoroutineContext = kotlin.coroutines.EmptyCoroutineContext

    override fun finish() = Unit

    override fun fail(throwable: Throwable) = Unit

    override fun cancel(reason: String?) = Unit

    override fun interrupt(reason: String) = Unit
}

internal fun noopSpanHandle(): SpanHandle = NoopSpanHandle

internal fun noopJourney(): Journey = NoopJourney
