package dev.journeyscope.core

import dev.journeyscope.core.model.DiagnosticMetadata
import dev.journeyscope.core.model.EventId
import dev.journeyscope.core.model.FailureInfo
import dev.journeyscope.core.model.JourneyEvent
import dev.journeyscope.core.model.JourneySpan
import dev.journeyscope.core.model.JourneySummary
import dev.journeyscope.core.model.JourneyTrace
import dev.journeyscope.core.model.SpanCategory
import dev.journeyscope.core.model.SpanId
import dev.journeyscope.core.model.SpanStatus
import dev.journeyscope.core.model.TraceId
import dev.journeyscope.core.model.TraceStatus
import dev.journeyscope.core.recorder.JourneyRecorder
import dev.journeyscope.core.recorder.RecordedTrace
import dev.journeyscope.core.timing.JourneyClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.CoroutineContext

internal class EngineDeps(
    val config: JourneyScopeConfig,
    val clock: JourneyClock,
    val recorder: JourneyRecorder,
    private val ids: IdGenerator,
) {
    val sanitizer = Sanitizer(config)

    fun newId(kind: String): String = "$kind-${ids.nextId()}"
}

// Plain class: StateFlow CAS compares with equals, so identity semantics are intended.
internal class TraceState(val trace: JourneyTrace, val nextSequence: Long, val detached: Boolean) {
    val accepting: Boolean get() = !detached && !trace.status.isTerminal

    fun with(trace: JourneyTrace, consumedSequence: Boolean = false): TraceState =
        TraceState(trace, if (consumedSequence) nextSequence + 1 else nextSequence, detached)

    fun activated(): TraceState =
        if (trace.status == TraceStatus.CREATED) with(trace.copy(status = TraceStatus.RUNNING)) else this

    fun detach(): TraceState = TraceState(trace, nextSequence, true)
}

internal class RecordingJourney(
    override val id: TraceId,
    override val name: String,
    private val deps: EngineDeps,
    metadata: DiagnosticMetadata,
) : Journey(), RecordedTrace {
    private val state: MutableStateFlow<TraceState>

    init {
        val now = deps.clock.monotonicNanos()
        val wall = deps.clock.wallClockMillis()
        state = MutableStateFlow(
            TraceState(
                trace = JourneyTrace(
                    id = id,
                    name = name,
                    status = TraceStatus.CREATED,
                    startNanos = now,
                    startWallMillis = wall,
                    endNanos = null,
                    endWallMillis = null,
                    spans = emptyList(),
                    events = emptyList(),
                    failure = null,
                    metadata = metadata,
                    droppedItems = 0,
                    summary = null,
                ),
                nextSequence = 0,
                detached = false,
            ),
        )
    }

    override val status: TraceStatus get() = state.value.trace.status
    override val isFinished: Boolean get() = status.isTerminal
    override val isRecording: Boolean get() = true

    override fun snapshot(): JourneyTrace = state.value.trace.ordered()

    override fun start() {
        val changed = mutate { s ->
            if (s.trace.status.canTransitionTo(TraceStatus.RUNNING)) s.activated() to true else s to false
        }
        if (changed) notifyChanged()
    }

    override fun recordEvent(name: String, category: SpanCategory, metadata: DiagnosticMetadata, span: SpanHandle?) {
        val now = deps.clock.monotonicNanos()
        val wall = deps.clock.wallClockMillis()
        val eventId = EventId(deps.newId("e"))
        val label = deps.sanitizer.label(name)
        val meta = deps.sanitizer.metadata(metadata)
        val spanId = span?.takeIf { it.isRecording }?.id
        val admitted = mutate { s ->
            admit(s) { trace, sequence ->
                trace.copy(events = trace.events + JourneyEvent(eventId, label, category, sequence, now, wall, spanId, meta))
            }
        }
        if (admitted) notifyChanged()
    }

    override fun openSpan(name: String, category: SpanCategory, metadata: DiagnosticMetadata, parentId: SpanId?): SpanHandle {
        val now = deps.clock.monotonicNanos()
        val wall = deps.clock.wallClockMillis()
        val spanId = SpanId(deps.newId("s"))
        val label = deps.sanitizer.label(name)
        val meta = deps.sanitizer.metadata(metadata)
        val admitted = mutate { s ->
            admit(s) { trace, sequence ->
                val parent = parentId?.takeIf { candidate -> trace.spans.any { it.id == candidate } }
                trace.copy(
                    spans = trace.spans + JourneySpan(
                        id = spanId,
                        parentId = parent,
                        name = label,
                        category = category,
                        sequence = sequence,
                        startNanos = now,
                        startWallMillis = wall,
                        endNanos = null,
                        status = SpanStatus.RUNNING,
                        failure = null,
                        endedBySystem = false,
                        metadata = meta,
                    ),
                )
            }
        }
        if (!admitted) return NoopSpanHandle
        notifyChanged()
        return RecordingSpanHandle(this, spanId)
    }

    override fun handleFor(id: SpanId): SpanHandle = RecordingSpanHandle(this, id)

    override fun context(parent: SpanHandle?): CoroutineContext = TraceContext(this, parent?.takeIf { it.isRecording }?.id)

    override fun finish() = terminate(TraceStatus.COMPLETED, null)

    override fun fail(throwable: Throwable) = terminate(TraceStatus.FAILED, failureOf(throwable))

    override fun cancel(reason: String?) = terminate(TraceStatus.CANCELLED, reason?.let { FailureInfo(CANCELLED_TYPE, deps.sanitizer.label(it)) })

    override fun interrupt(reason: String) = terminate(TraceStatus.INTERRUPTED, FailureInfo(INTERRUPTED_TYPE, deps.sanitizer.label(reason)))

    override fun detach() {
        mutate { s -> s.detach() to Unit }
    }

    internal fun failureOf(throwable: Throwable): FailureInfo? {
        return try {
            deps.sanitizer.failure(throwable)
        } catch (ignored: Throwable) {
            null
        }
    }

    internal fun endSpan(spanId: SpanId, status: SpanStatus, failure: FailureInfo?, metadata: DiagnosticMetadata = DiagnosticMetadata.Empty) {
        val extra = if (metadata.isEmpty) metadata else deps.sanitizer.metadata(metadata)
        val now = deps.clock.monotonicNanos()
        val changed = mutate { s ->
            val target = s.trace.spans.firstOrNull { it.id == spanId }
            if (s.detached || target == null || target.endNanos != null) {
                s to false
            } else {
                val merged = if (extra.isEmpty) {
                    target.metadata
                } else {
                    deps.sanitizer.metadata(DiagnosticMetadata(target.metadata.entries + extra.entries, target.metadata.truncated || extra.truncated))
                }
                val closed = target.copy(endNanos = now, status = status, failure = failure, metadata = merged)
                s.with(s.trace.copy(spans = s.trace.spans.map { if (it.id == spanId) closed else it })) to true
            }
        }
        if (changed) notifyChanged()
    }

    internal fun recordSpanEvent(spanId: SpanId, name: String, category: SpanCategory, metadata: DiagnosticMetadata) {
        recordEvent(name, category, metadata, RecordingSpanHandle(this, spanId))
    }

    private fun terminate(target: TraceStatus, failure: FailureInfo?) {
        val now = deps.clock.monotonicNanos()
        val wall = deps.clock.wallClockMillis()
        val finished = mutate<JourneyTrace?> { s ->
            if (s.detached || !s.trace.status.canTransitionTo(target)) {
                s to null
            } else {
                val closedSpans = s.trace.spans.map { span ->
                    if (span.endNanos != null) {
                        span
                    } else {
                        span.copy(endNanos = now, status = openSpanStatusFor(target), endedBySystem = true)
                    }
                }
                val ended = s.trace.copy(
                    status = target,
                    endNanos = now,
                    endWallMillis = wall,
                    spans = closedSpans,
                    failure = failure,
                )
                s.with(ended.copy(summary = summarize(ended))) to ended
            }
        }
        if (finished != null) {
            safely { deps.recorder.notifyFinished(state.value.trace.ordered()) }
        }
    }

    private fun openSpanStatusFor(target: TraceStatus): SpanStatus =
        if (target == TraceStatus.CANCELLED) SpanStatus.CANCELLED else SpanStatus.INTERRUPTED

    private fun summarize(trace: JourneyTrace): JourneySummary {
        val timing = trace.copy(summary = null).timing()
        return JourneySummary(
            name = trace.name,
            status = trace.status,
            duration = timing.duration,
            spanCount = trace.spans.size,
            eventCount = trace.events.size,
            failedSpanCount = trace.spans.count { it.status == SpanStatus.FAILED },
            droppedItems = trace.droppedItems,
            isSlow = timing.duration >= deps.config.slowJourneyThreshold,
            criticalPathDuration = timing.criticalPathDuration,
            failureType = trace.failure?.type,
        )
    }

    private fun admit(s: TraceState, build: (JourneyTrace, Long) -> JourneyTrace): Pair<TraceState, Boolean> {
        if (s.detached) return s to false
        if (!s.accepting) return s.with(s.trace.copy(droppedItems = s.trace.droppedItems + 1)) to false
        val active = s.activated()
        val used = active.trace.spans.size + active.trace.events.size
        if (used >= deps.config.maxEventsPerTrace) {
            return active.with(active.trace.copy(droppedItems = active.trace.droppedItems + 1)) to false
        }
        return active.with(build(active.trace, active.nextSequence), consumedSequence = true) to true
    }

    private inline fun <R> mutate(block: (TraceState) -> Pair<TraceState, R>): R {
        while (true) {
            val current = state.value
            val (next, result) = block(current)
            if (next === current || state.compareAndSet(current, next)) return result
        }
    }

    private fun notifyChanged() {
        safely { deps.recorder.notifyChanged(id) }
    }

    private fun JourneyTrace.ordered(): JourneyTrace = copy(
        spans = spans.sortedWith(compareBy({ it.startNanos }, { it.sequence })),
        events = events.sortedWith(compareBy({ it.atNanos }, { it.sequence })),
    )

    private companion object {
        const val CANCELLED_TYPE = "Cancelled"
        const val INTERRUPTED_TYPE = "Interrupted"
    }
}

internal class RecordingSpanHandle(
    private val journey: RecordingJourney,
    override val id: SpanId,
) : SpanHandle() {
    override val isRecording: Boolean get() = true

    override fun end(status: SpanStatus, metadata: DiagnosticMetadata) = journey.endSpan(id, status, null, metadata)

    override fun fail(throwable: Throwable, metadata: DiagnosticMetadata) =
        journey.endSpan(id, SpanStatus.FAILED, journey.failureOf(throwable), metadata)

    override fun recordEvent(name: String, category: SpanCategory, metadata: DiagnosticMetadata) =
        journey.recordSpanEvent(id, name, category, metadata)
}
