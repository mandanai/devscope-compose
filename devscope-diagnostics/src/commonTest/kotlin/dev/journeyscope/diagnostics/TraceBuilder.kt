package dev.journeyscope.diagnostics

import dev.journeyscope.core.expectation.ExpectedSequence
import dev.journeyscope.core.model.DiagnosticMetadata
import dev.journeyscope.core.model.EventId
import dev.journeyscope.core.model.FailureInfo
import dev.journeyscope.core.model.JourneyEvent
import dev.journeyscope.core.model.JourneySpan
import dev.journeyscope.core.model.JourneyTrace
import dev.journeyscope.core.model.SpanCategory
import dev.journeyscope.core.model.SpanId
import dev.journeyscope.core.model.SpanStatus
import dev.journeyscope.core.model.TraceId
import dev.journeyscope.core.model.TraceStatus
import dev.journeyscope.core.model.diagnosticMetadata

private const val MS = 1_000_000L

class TraceBuilder(
    private val status: TraceStatus = TraceStatus.COMPLETED,
) {
    private val spans = mutableListOf<JourneySpan>()
    private val events = mutableListOf<JourneyEvent>()
    private var sequence = 0L
    private var end = 0L

    fun span(
        id: String,
        category: SpanCategory,
        startMs: Long,
        durationMs: Long?,
        parent: String? = null,
        status: SpanStatus = SpanStatus.OK,
        endedBySystem: Boolean = false,
        metadata: DiagnosticMetadata = DiagnosticMetadata.Empty,
        name: String = id,
    ): TraceBuilder {
        val endMs = durationMs?.let { startMs + it }
        endMs?.let { end = maxOf(end, it) }
        spans +=
            JourneySpan(
                id = SpanId(id),
                parentId = parent?.let(::SpanId),
                name = name,
                category = category,
                sequence = sequence++,
                startNanos = startMs * MS,
                startWallMillis = startMs,
                endNanos = endMs?.times(MS),
                status = if (durationMs == null) SpanStatus.RUNNING else status,
                failure = if (status == SpanStatus.FAILED) FailureInfo("Failure") else null,
                endedBySystem = endedBySystem,
                metadata = metadata,
            )
        return this
    }

    fun event(
        name: String,
        category: SpanCategory,
        atMs: Long,
    ): TraceBuilder {
        end = maxOf(end, atMs)
        events += JourneyEvent(EventId("e$sequence"), name, category, sequence++, atMs * MS, atMs, null, DiagnosticMetadata.Empty)
        return this
    }

    fun build(totalMs: Long? = null): JourneyTrace =
        JourneyTrace(
            id = TraceId("t"),
            name = "journey",
            status = status,
            startNanos = 0,
            startWallMillis = 0,
            endNanos = if (status.isTerminal) (totalMs ?: end) * MS else null,
            endWallMillis = null,
            spans = spans.toList(),
            events = events.toList(),
            failure = null,
            metadata = DiagnosticMetadata.Empty,
            droppedItems = 0,
            summary = null,
        )
}

fun network(
    method: String = "POST",
    host: String = "api.shop",
    path: String = "/cart",
    status: Long? = null,
    failure: String? = null,
    business: String? = null,
) = diagnosticMetadata {
    put("http.method", method)
    put("http.host", host)
    put("http.path", path)
    status?.let { put("http.status", it) }
    failure?.let { put("http.failure", it) }
    business?.let { put("business.status", it) }
}

fun addToCartDemo(): JourneyTrace =
    TraceBuilder()
        .span("ui", SpanCategory.UI_INTERACTION, 0, 0)
        .span("vm", SpanCategory.VIEW_MODEL, 0, 12)
        .span("uc", SpanCategory.USE_CASE, 12, 34)
        .span("api", SpanCategory.NETWORK, 46, 940, metadata = network(status = 200))
        .span("val", SpanCategory.BUSINESS_VALIDATION, 986, 4)
        .span("db", SpanCategory.DATABASE, 990, 150)
        .span("emit", SpanCategory.STATE_EMISSION, 1140, 15)
        .span("render", SpanCategory.UI_RENDER, 1155, 110)
        .build()

fun analyzer(config: DiagnosticConfig = DiagnosticConfig()) = DiagnosticAnalyzer(config)

fun DiagnosticReport.ids() = findings.map { it.ruleId }

fun sequenceOf(vararg labels: String) =
    ExpectedSequence(
        "Add to cart",
        labels.map {
            dev.journeyscope.core.expectation.ExpectedStep
                .named(it, it)
        },
    )
