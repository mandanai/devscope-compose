package dev.journeyscope.export.schema

import dev.journeyscope.core.JourneyScopeConfig
import dev.journeyscope.core.model.DiagnosticMetadata
import dev.journeyscope.core.model.JourneyTrace
import dev.journeyscope.core.model.MetadataValue
import dev.journeyscope.export.json.JsonValue
import dev.journeyscope.export.json.JsonWriter
import dev.journeyscope.export.redaction.RedactedTrace
import dev.journeyscope.export.redaction.ValueScrubber

/** Serializes already-redacted traces to the versioned report format. */
public object TraceReportWriter {
    public fun write(
        traces: List<RedactedTrace>,
        context: ReportContext = ReportContext(),
    ): String = JsonWriter.write(document(traces, context))

    internal fun document(
        traces: List<RedactedTrace>,
        context: ReportContext,
    ): JsonValue.Obj {
        val fields = LinkedHashMap<String, JsonValue>()
        fields[Keys.SCHEMA] = JsonValue.Text(TraceSchema.NAME)
        fields[Keys.VERSION] = JsonValue.Integer(TraceSchema.VERSION.toLong())
        fields[Keys.SDK_VERSION] = JsonValue.Text(context.sdkVersion)
        fields[Keys.EXPORTED_AT] = JsonValue.Integer(context.exportedAtWallMillis)
        fields[Keys.PLATFORM] = JsonValue.Text(context.platform)
        context.appVersion?.let { fields[Keys.APP_VERSION] = JsonValue.Text(it) }
        context.config?.let { fields[Keys.CONFIG] = config(it) }
        fields[Keys.TRACES] = JsonValue.Array(traces.map { trace(it.trace, context) })
        return JsonValue.Obj(fields)
    }

    private fun config(config: JourneyScopeConfig): JsonValue.Obj =
        JsonValue.Obj(
            linkedMapOf(
                "enabled" to JsonValue.Bool(config.enabled),
                "samplingRate" to JsonValue.Decimal(config.samplingRate),
                "maxTraces" to JsonValue.Integer(config.maxTraces.toLong()),
                "maxEventsPerTrace" to JsonValue.Integer(config.maxEventsPerTrace.toLong()),
                "maxMetadataSize" to JsonValue.Integer(config.maxMetadataSize.toLong()),
                "slowJourneyThresholdMillis" to JsonValue.Integer(config.slowJourneyThreshold.inWholeMilliseconds),
                "captureStackTraces" to JsonValue.Bool(config.captureStackTraces),
                "recordFailures" to JsonValue.Bool(config.recordFailures),
                "maxEventsPerSecond" to (config.maxEventsPerSecond?.let { JsonValue.Integer(it.toLong()) } ?: JsonValue.Null),
            ),
        )

    private fun trace(
        trace: JourneyTrace,
        context: ReportContext,
    ): JsonValue.Obj {
        val base = trace.startNanos
        val fields = LinkedHashMap<String, JsonValue>()
        fields[Keys.ID] = JsonValue.Text(trace.id.value)
        fields[Keys.NAME] = JsonValue.Text(trace.name)
        fields[Keys.STATUS] = JsonValue.Text(trace.status.name)
        fields[Keys.PLATFORM] = JsonValue.Text(context.platform)
        fields[Keys.START_WALL] = JsonValue.Integer(trace.startWallMillis)
        trace.endWallMillis?.let { fields[Keys.END_WALL] = JsonValue.Integer(it) }
        fields[Keys.DURATION] = JsonValue.Integer(((trace.endNanos ?: trace.lastObservedNanos) - base).coerceAtLeast(0))
        fields[Keys.DROPPED] = JsonValue.Integer(trace.droppedItems.toLong())
        trace.failure?.let { fields[Keys.FAILURE] = JsonValue.Obj(mapOf(Keys.FAILURE_TYPE to JsonValue.Text(it.type))) }
        putMetadata(fields, trace.metadata)
        fields[Keys.SPANS] =
            JsonValue.Array(
                trace.spans.map { span ->
                    val s = LinkedHashMap<String, JsonValue>()
                    s[Keys.ID] = JsonValue.Text(span.id.value)
                    span.parentId?.let { s[Keys.PARENT_ID] = JsonValue.Text(it.value) }
                    s[Keys.NAME] = JsonValue.Text(span.name)
                    s[Keys.CATEGORY] = JsonValue.Text(span.category.name)
                    s[Keys.SEQUENCE] = JsonValue.Integer(span.sequence)
                    s[Keys.START_WALL] = JsonValue.Integer(span.startWallMillis)
                    s[Keys.START_OFFSET] = JsonValue.Integer((span.startNanos - base).coerceAtLeast(0))
                    span.endNanos?.let { s[Keys.END_OFFSET] = JsonValue.Integer((it - base).coerceAtLeast(0)) }
                    s[Keys.STATUS] = JsonValue.Text(span.status.name)
                    span.failure?.let { s[Keys.FAILURE] = JsonValue.Obj(mapOf(Keys.FAILURE_TYPE to JsonValue.Text(it.type))) }
                    s[Keys.ENDED_BY_SYSTEM] = JsonValue.Bool(span.endedBySystem)
                    putMetadata(s, span.metadata)
                    JsonValue.Obj(s)
                },
            )
        fields[Keys.EVENTS] =
            JsonValue.Array(
                trace.events.map { event ->
                    val e = LinkedHashMap<String, JsonValue>()
                    e[Keys.ID] = JsonValue.Text(event.id.value)
                    event.spanId?.let { e[Keys.SPAN_ID] = JsonValue.Text(it.value) }
                    e[Keys.NAME] = JsonValue.Text(event.name)
                    e[Keys.CATEGORY] = JsonValue.Text(event.category.name)
                    e[Keys.SEQUENCE] = JsonValue.Integer(event.sequence)
                    e[Keys.OFFSET] = JsonValue.Integer((event.atNanos - base).coerceAtLeast(0))
                    e[Keys.WALL] = JsonValue.Integer(event.atWallMillis)
                    putMetadata(e, event.metadata)
                    JsonValue.Obj(e)
                },
            )
        context.findings[trace.id.value]?.takeIf { it.isNotEmpty() }?.let { findings ->
            fields[Keys.FINDINGS] =
                JsonValue.Array(
                    findings.map {
                        val f = LinkedHashMap<String, JsonValue>()
                        f[Keys.RULE] = JsonValue.Text(ValueScrubber.scrub(it.ruleId).text)
                        f[Keys.SEVERITY] = JsonValue.Text(it.severity)
                        f[Keys.TITLE] = JsonValue.Text(ValueScrubber.scrub(it.title).text)
                        it.spanId?.let { id -> f[Keys.SPAN_ID] = JsonValue.Text(id) }
                        JsonValue.Obj(f)
                    },
                )
        }
        return JsonValue.Obj(fields)
    }

    private fun putMetadata(
        target: MutableMap<String, JsonValue>,
        metadata: DiagnosticMetadata,
    ) {
        target[Keys.METADATA] =
            JsonValue.Obj(
                metadata.entries.mapValuesTo(LinkedHashMap()) { (_, value) ->
                    when (value) {
                        is MetadataValue.Text -> JsonValue.Text(value.value)
                        is MetadataValue.Integer -> JsonValue.Integer(value.value)
                        is MetadataValue.Decimal -> if (value.value.isFinite()) JsonValue.Decimal(value.value) else JsonValue.Null
                        is MetadataValue.Flag -> JsonValue.Bool(value.value)
                    }
                },
            )
        if (metadata.truncated) target[Keys.METADATA_TRUNCATED] = JsonValue.Bool(true)
    }
}
