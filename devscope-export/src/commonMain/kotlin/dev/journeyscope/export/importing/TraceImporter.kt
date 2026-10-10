package dev.journeyscope.export.importing

import dev.journeyscope.core.model.DiagnosticMetadata
import dev.journeyscope.core.model.EventId
import dev.journeyscope.core.model.FailureInfo
import dev.journeyscope.core.model.JourneyEvent
import dev.journeyscope.core.model.JourneySpan
import dev.journeyscope.core.model.JourneySummary
import dev.journeyscope.core.model.JourneyTrace
import dev.journeyscope.core.model.MetadataValue
import dev.journeyscope.core.model.SpanCategory
import dev.journeyscope.core.model.SpanId
import dev.journeyscope.core.model.SpanStatus
import dev.journeyscope.core.model.TraceId
import dev.journeyscope.core.model.TraceStatus
import dev.journeyscope.export.json.JsonException
import dev.journeyscope.export.json.JsonLimits
import dev.journeyscope.export.json.JsonReader
import dev.journeyscope.export.json.JsonValue
import dev.journeyscope.export.redaction.RedactionPipeline
import dev.journeyscope.export.schema.ExportedFinding
import dev.journeyscope.export.schema.Keys
import dev.journeyscope.export.schema.SchemaMigrator
import dev.journeyscope.export.schema.TraceSchema
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Treats the document as hostile: bounded parse, strict shape checks, graph validation, then re-redaction. */
public class TraceImporter(
    private val limits: ImportLimits = ImportLimits(),
    private val redaction: RedactionPipeline = RedactionPipeline(),
    private val migrator: SchemaMigrator = SchemaMigrator(),
) {
    public fun import(text: String): ImportResult {
        if (text.length > limits.maxBytes) return reject("document exceeds ${limits.maxBytes} bytes")
        val root =
            try {
                JsonReader.parse(text, JsonLimits(limits.maxBytes, limits.maxDepth, MAX_NODES, limits.maxStringLength))
            } catch (e: JsonException) {
                return reject("malformed JSON: ${e.message}")
            }
        if (root !is JsonValue.Obj) return reject("root must be an object")
        if ((root.fields[Keys.SCHEMA] as? JsonValue.Text)?.value != TraceSchema.NAME) return reject("not a ${TraceSchema.NAME} document")
        val document =
            when (val outcome = migrator.migrate(root)) {
                is SchemaMigrator.Outcome.Ready -> outcome.document
                is SchemaMigrator.Outcome.Unsupported -> return reject(outcome.reason)
            }
        return Session(document).run()
    }

    private fun reject(issue: String): ImportResult = ImportResult.Rejected(listOf(issue))

    private inner class Session(
        private val document: JsonValue.Obj,
    ) {
        private val issues = ArrayList<String>()
        private val warnings = ArrayList<String>()

        fun run(): ImportResult {
            unknownFields(document, Keys.ENVELOPE, "document")
            val slowThreshold = slowThreshold()
            val traceValues = (document.fields[Keys.TRACES] as? JsonValue.Array)?.items
            if (traceValues == null) {
                issue("traces must be an array")
                return finish(emptyList(), emptyMap())
            }
            if (traceValues.size > limits.maxTraces) {
                issue("more than ${limits.maxTraces} traces")
                return finish(emptyList(), emptyMap())
            }
            val traces = ArrayList<JourneyTrace>()
            val findings = LinkedHashMap<String, List<ExportedFinding>>()
            val seen = HashSet<String>()
            traceValues.forEachIndexed { index, value ->
                val label = "traces[$index]"
                val parsed =
                    (value as? JsonValue.Obj)?.let { parseTrace(it, label, slowThreshold) } ?: run {
                        if (value !is JsonValue.Obj) issue("$label must be an object")
                        null
                    }
                if (parsed != null) {
                    if (!seen.add(parsed.first.id.value)) {
                        issue("$label: duplicate trace id")
                    } else {
                        traces += parsed.first
                        findings[parsed.first.id.value] = parsed.second
                    }
                }
            }
            return finish(traces, findings)
        }

        private fun finish(
            traces: List<JourneyTrace>,
            findings: Map<String, List<ExportedFinding>>,
        ): ImportResult {
            if (issues.isNotEmpty()) return ImportResult.Rejected(issues.toList())
            val safe = traces.map { redaction.redact(it).trace }
            return ImportResult.Accepted(safe, findings, warnings.toList())
        }

        private fun slowThreshold() =
            ((document.fields[Keys.CONFIG] as? JsonValue.Obj)?.fields?.get("slowJourneyThresholdMillis") as? JsonValue.Integer)
                ?.value
                ?.takeIf { it in 1..MAX_THRESHOLD_MILLIS }
                ?.milliseconds ?: DEFAULT_SLOW

        private fun parseTrace(
            obj: JsonValue.Obj,
            label: String,
            slowThreshold: kotlin.time.Duration,
        ): Pair<JourneyTrace, List<ExportedFinding>>? {
            val before = issues.size
            unknownFields(obj, Keys.TRACE, label)
            val id = id(obj, Keys.ID, label)
            val name = text(obj, Keys.NAME, label)
            val status = enumValue<TraceStatus>(obj, Keys.STATUS, label)
            val startWall = long(obj, Keys.START_WALL, label, min = 0)
            val endWall = long(obj, Keys.END_WALL, label, min = 0, required = false)
            val duration = long(obj, Keys.DURATION, label, min = 0)
            val dropped = long(obj, Keys.DROPPED, label, min = 0, required = false) ?: 0
            val failure = failure(obj, label)
            val metadata = metadata(obj, label)
            val spanValues = array(obj, Keys.SPANS, label, limits.maxSpansPerTrace)
            val eventValues = array(obj, Keys.EVENTS, label, limits.maxEventsPerTrace)
            if (issues.size > before || id == null || name == null || status == null || startWall == null || duration == null) return null

            val spans = spanValues.orEmpty().mapIndexedNotNull { i, v -> parseSpan(v, "$label.spans[$i]", duration) }
            val events = eventValues.orEmpty().mapIndexedNotNull { i, v -> parseEvent(v, "$label.events[$i]", duration) }
            if (issues.size > before) return null
            validateGraph(label, spans, events)
            if (issues.size > before) return null

            if (!status.isTerminal) warnings += "$label: in-progress journey imported as INTERRUPTED"
            val finalStatus = if (status.isTerminal) status else TraceStatus.INTERRUPTED
            val closedSpans =
                spans.map {
                    if (it.endNanos != null) it else it.copy(endNanos = duration, status = SpanStatus.INTERRUPTED, endedBySystem = true)
                }
            val raw =
                JourneyTrace(
                    id = TraceId(id),
                    name = name,
                    status = finalStatus,
                    startNanos = 0,
                    startWallMillis = startWall,
                    endNanos = duration,
                    endWallMillis = endWall,
                    spans = closedSpans,
                    events = events,
                    failure = failure,
                    metadata = metadata,
                    droppedItems = dropped.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    summary = null,
                )
            return raw.copy(summary = summarize(raw, slowThreshold)) to findings(obj, label)
        }

        private fun parseSpan(
            value: JsonValue,
            label: String,
            duration: Long,
        ): JourneySpan? {
            val obj = value as? JsonValue.Obj ?: return null.also { issue("$label must be an object") }
            val before = issues.size
            unknownFields(obj, Keys.SPAN, label)
            val id = id(obj, Keys.ID, label)
            val parent = id(obj, Keys.PARENT_ID, label, required = false)
            val name = text(obj, Keys.NAME, label)
            val category = category(obj, label)
            val sequence = long(obj, Keys.SEQUENCE, label, min = 0)
            val startWall = long(obj, Keys.START_WALL, label, min = 0)
            val start = long(obj, Keys.START_OFFSET, label, min = 0, max = duration)
            val end = long(obj, Keys.END_OFFSET, label, min = 0, max = duration, required = false)
            val status = enumValue<SpanStatus>(obj, Keys.STATUS, label)
            val failure = failure(obj, label)
            val system = (obj.fields[Keys.ENDED_BY_SYSTEM] as? JsonValue.Bool)?.value ?: false
            val metadata = metadata(obj, label)
            if (start != null && end != null && end < start) issue("$label: end before start")
            if (issues.size > before || id == null || name == null || category == null || sequence == null || start == null ||
                status == null
            ) {
                return null
            }
            return JourneySpan(
                SpanId(id),
                parent?.let(::SpanId),
                name,
                category,
                sequence,
                start,
                startWall ?: 0,
                end,
                status,
                failure,
                system,
                metadata,
            )
        }

        private fun parseEvent(
            value: JsonValue,
            label: String,
            duration: Long,
        ): JourneyEvent? {
            val obj = value as? JsonValue.Obj ?: return null.also { issue("$label must be an object") }
            val before = issues.size
            unknownFields(obj, Keys.EVENT, label)
            val id = id(obj, Keys.ID, label)
            val spanId = id(obj, Keys.SPAN_ID, label, required = false)
            val name = text(obj, Keys.NAME, label)
            val category = category(obj, label)
            val sequence = long(obj, Keys.SEQUENCE, label, min = 0)
            val offset = long(obj, Keys.OFFSET, label, min = 0, max = duration)
            val wall = long(obj, Keys.WALL, label, min = 0)
            val metadata = metadata(obj, label)
            if (issues.size > before || id == null || name == null || category == null || sequence == null || offset == null) return null
            return JourneyEvent(EventId(id), name, category, sequence, offset, wall ?: 0, spanId?.let(::SpanId), metadata)
        }

        private fun validateGraph(
            label: String,
            spans: List<JourneySpan>,
            events: List<JourneyEvent>,
        ) {
            val byId = HashMap<SpanId, JourneySpan>()
            for (span in spans) if (byId.put(span.id, span) != null) issue("$label: duplicate span id ${span.id}")
            for (span in spans) {
                val parentId = span.parentId ?: continue
                val parent = byId[parentId]
                if (parent == null) {
                    issue("$label: span ${span.id} references missing parent $parentId")
                } else {
                    if (span.startNanos < parent.startNanos) issue("$label: span ${span.id} starts before its parent")
                    val parentEnd = parent.endNanos
                    val spanEnd = span.endNanos
                    if (parentEnd != null && spanEnd != null &&
                        spanEnd > parentEnd
                    ) {
                        warnings += "$label: span ${span.id} outlives its parent"
                    }
                }
            }
            for (span in spans) {
                var cursor: SpanId? = span.parentId
                var hops = 0
                while (cursor != null) {
                    if (cursor == span.id || ++hops > spans.size) {
                        issue("$label: parent cycle at span ${span.id}")
                        break
                    }
                    cursor = byId[cursor]?.parentId
                }
            }
            val eventIds = HashSet<EventId>()
            for (event in events) {
                if (!eventIds.add(event.id)) issue("$label: duplicate event id ${event.id}")
                if (event.spanId != null &&
                    event.spanId !in byId
                ) {
                    issue("$label: event ${event.id} references missing span ${event.spanId}")
                }
            }
            val sequences = HashSet<Long>()
            for (s in spans) if (!sequences.add(s.sequence)) warnings += "$label: duplicate sequence ${s.sequence}"
            for (e in events) if (!sequences.add(e.sequence)) warnings += "$label: duplicate sequence ${e.sequence}"
        }

        private fun summarize(
            trace: JourneyTrace,
            slowThreshold: kotlin.time.Duration,
        ): JourneySummary {
            val timing = trace.timing()
            return JourneySummary(
                name = trace.name,
                status = trace.status,
                duration = timing.duration,
                spanCount = trace.spans.size,
                eventCount = trace.events.size,
                failedSpanCount = trace.spans.count { it.status == SpanStatus.FAILED },
                droppedItems = trace.droppedItems,
                isSlow = timing.duration >= slowThreshold,
                criticalPathDuration = timing.criticalPathDuration,
                failureType = trace.failure?.type,
            )
        }

        private fun findings(
            obj: JsonValue.Obj,
            label: String,
        ): List<ExportedFinding> {
            val items = (obj.fields[Keys.FINDINGS] as? JsonValue.Array)?.items ?: return emptyList()
            if (items.size > MAX_FINDINGS) {
                warnings += "$label: findings truncated to $MAX_FINDINGS"
            }
            return items.take(MAX_FINDINGS).mapNotNull { item ->
                val f = item as? JsonValue.Obj ?: return@mapNotNull null
                unknownFields(f, Keys.FINDING, "$label.findings")
                val rule = text(f, Keys.RULE, "$label.findings") ?: return@mapNotNull null
                val severity = text(f, Keys.SEVERITY, "$label.findings") ?: return@mapNotNull null
                val title = text(f, Keys.TITLE, "$label.findings") ?: return@mapNotNull null
                ExportedFinding(rule, severity, title, id(f, Keys.SPAN_ID, "$label.findings", required = false))
            }
        }

        private fun failure(
            obj: JsonValue.Obj,
            label: String,
        ): FailureInfo? {
            val failure = obj.fields[Keys.FAILURE] ?: return null
            val type = ((failure as? JsonValue.Obj)?.fields?.get(Keys.FAILURE_TYPE) as? JsonValue.Text)?.value
            if (type == null || type.length > limits.maxStringLength) {
                issue("$label.failure.type is invalid")
                return null
            }
            return FailureInfo(type)
        }

        private fun metadata(
            obj: JsonValue.Obj,
            label: String,
        ): DiagnosticMetadata {
            val raw = obj.fields[Keys.METADATA] ?: return DiagnosticMetadata.Empty
            val fields =
                (raw as? JsonValue.Obj)?.fields ?: return DiagnosticMetadata.Empty.also { issue("$label.metadata must be an object") }
            if (fields.size > limits.maxMetadataEntries) {
                issue("$label.metadata has more than ${limits.maxMetadataEntries} entries")
                return DiagnosticMetadata.Empty
            }
            val entries = LinkedHashMap<String, MetadataValue>()
            for ((key, value) in fields) {
                if (key.length > MAX_KEY_LENGTH) {
                    issue("$label.metadata key too long")
                    continue
                }
                val converted = metadataValue(value)
                if (converted == null) issue("$label.metadata.$key has an unsupported value") else entries[key] = converted
            }
            val truncated = (obj.fields[Keys.METADATA_TRUNCATED] as? JsonValue.Bool)?.value ?: false
            return DiagnosticMetadata(entries, truncated)
        }

        private fun metadataValue(value: JsonValue): MetadataValue? =
            when (value) {
                is JsonValue.Text -> if (value.value.length <= limits.maxStringLength) MetadataValue.Text(value.value) else null
                is JsonValue.Integer -> MetadataValue.Integer(value.value)
                is JsonValue.Decimal -> MetadataValue.Decimal(value.value)
                is JsonValue.Bool -> MetadataValue.Flag(value.value)
                else -> null
            }

        private fun array(
            obj: JsonValue.Obj,
            key: String,
            label: String,
            max: Int,
        ): List<JsonValue>? {
            val items = (obj.fields[key] as? JsonValue.Array)?.items
            if (items == null) {
                issue("$label.$key must be an array")
                return null
            }
            if (items.size > max) {
                issue("$label.$key has more than $max items")
                return null
            }
            return items
        }

        private fun id(
            obj: JsonValue.Obj,
            key: String,
            label: String,
            required: Boolean = true,
        ): String? {
            val value = obj.fields[key]
            if (value == null || value == JsonValue.Null) {
                if (required) issue("$label.$key is required")
                return null
            }
            val text = (value as? JsonValue.Text)?.value
            if (text == null || !ID_PATTERN.matches(text)) {
                issue("$label.$key is not a valid identifier")
                return null
            }
            return text
        }

        private fun text(
            obj: JsonValue.Obj,
            key: String,
            label: String,
        ): String? {
            val text = (obj.fields[key] as? JsonValue.Text)?.value
            if (text == null || text.length > limits.maxStringLength) {
                issue("$label.$key must be a string of at most ${limits.maxStringLength} characters")
                return null
            }
            return text
        }

        private fun category(
            obj: JsonValue.Obj,
            label: String,
        ): SpanCategory? {
            val text = (obj.fields[Keys.CATEGORY] as? JsonValue.Text)?.value
            if (text == null || !CATEGORY_PATTERN.matches(text)) {
                issue("$label.category is invalid")
                return null
            }
            return SpanCategory(text)
        }

        private fun long(
            obj: JsonValue.Obj,
            key: String,
            label: String,
            min: Long,
            max: Long = Long.MAX_VALUE,
            required: Boolean = true,
        ): Long? {
            val value = obj.fields[key]
            if (value == null || value == JsonValue.Null) {
                if (required) issue("$label.$key is required")
                return null
            }
            val number = (value as? JsonValue.Integer)?.value
            if (number == null || number < min || number > max) {
                issue("$label.$key is out of range")
                return null
            }
            return number
        }

        private inline fun <reified E : Enum<E>> enumValue(
            obj: JsonValue.Obj,
            key: String,
            label: String,
        ): E? {
            val name = (obj.fields[key] as? JsonValue.Text)?.value
            val match = enumValues<E>().firstOrNull { it.name == name }
            if (match == null) issue("$label.$key is not a supported value")
            return match
        }

        private fun unknownFields(
            obj: JsonValue.Obj,
            known: Set<String>,
            label: String,
        ) {
            val unknown = obj.fields.keys.filterNot { it in known }
            if (unknown.isEmpty()) return
            val message = "$label has unsupported fields: ${unknown.take(MAX_LISTED_UNKNOWN).joinToString()}"
            if (limits.strict) issue(message) else warnings += message
        }

        private fun issue(message: String) {
            if (issues.size < limits.maxIssues) issues += message
        }
    }

    private companion object {
        val ID_PATTERN = Regex("[A-Za-z0-9_.:-]{1,64}")
        val CATEGORY_PATTERN = Regex("[A-Za-z0-9_.-]{1,64}")
        val DEFAULT_SLOW = 2.seconds
        const val MAX_NODES = 200_000
        const val MAX_FINDINGS = 100
        const val MAX_KEY_LENGTH = 64
        const val MAX_LISTED_UNKNOWN = 5
        const val MAX_THRESHOLD_MILLIS = 3_600_000L
    }
}
