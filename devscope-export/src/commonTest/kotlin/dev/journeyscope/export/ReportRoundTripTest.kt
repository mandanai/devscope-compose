package dev.journeyscope.export

import dev.journeyscope.export.importing.ImportResult
import dev.journeyscope.export.importing.TraceImporter
import dev.journeyscope.export.redaction.RedactionPipeline
import dev.journeyscope.export.schema.ExportedFinding
import dev.journeyscope.export.schema.ReportContext
import dev.journeyscope.export.schema.SchemaMigrator
import dev.journeyscope.export.schema.TraceReportWriter
import dev.journeyscope.export.json.JsonValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReportRoundTripTest {
    private val pipeline = RedactionPipeline()

    @Test
    fun exportThenImportPreservesStructureTimingAndCorrelation() {
        val original = pipeline.redact(sampleTrace())
        val json = TraceReportWriter.write(
            listOf(original),
            ReportContext(appVersion = "1.2.3", findings = mapOf(original.trace.id.value to listOf(ExportedFinding("slow", "WARN", "Slow call")))),
        )
        val result = assertIs<ImportResult.Accepted>(TraceImporter().import(json))
        val imported = result.traces.single()
        val source = original.trace

        assertEquals(source.id, imported.id)
        assertEquals(source.status, imported.status)
        assertEquals(source.spans.map { it.id to it.parentId }, imported.spans.map { it.id to it.parentId })
        assertEquals(source.spans.map { it.sequence }, imported.spans.map { it.sequence })
        assertEquals(source.events.map { it.id to it.spanId }, imported.events.map { it.id to it.spanId })
        assertEquals(source.spans.map { it.startNanos - source.startNanos }, imported.spans.map { it.startNanos })
        assertEquals(source.spans.map { it.endNanos?.minus(source.startNanos) }, imported.spans.map { it.endNanos })
        assertEquals(source.timing().duration, imported.timing().duration)
        assertEquals("slow", result.findings.getValue(source.id.value).single().ruleId)
    }

    @Test
    fun metadataTypesSurviveTheRoundTrip() {
        val trace = pipeline.redact(sampleTrace()).trace.spans.first { it.metadata.entries.isNotEmpty() }
        assertTrue(trace.metadata.entries.values.any { it is dev.journeyscope.core.model.MetadataValue.Integer })
        val json = TraceReportWriter.write(listOf(pipeline.redact(sampleTrace())))
        val imported = (TraceImporter().import(json) as ImportResult.Accepted).traces.single()
        val status = imported.spans.mapNotNull { it.metadata["http.status"] }.single()
        assertEquals(dev.journeyscope.core.model.MetadataValue.Integer(200), status)
    }

    @Test
    fun migratorAppliesStepsInOrderAndRejectsNewerVersions() {
        val migrator = SchemaMigrator(
            currentVersion = 3,
            migrations = mapOf(
                1 to { doc -> JsonValue.Obj(doc.fields + ("a" to JsonValue.Bool(true))) },
                2 to { doc -> JsonValue.Obj(doc.fields + ("b" to JsonValue.Bool(true))) },
            ),
        )
        val ready = assertIs<SchemaMigrator.Outcome.Ready>(migrator.migrate(JsonValue.Obj(mapOf("schemaVersion" to JsonValue.Integer(1)))))
        assertEquals(JsonValue.Integer(3), ready.document.fields["schemaVersion"])
        assertTrue("a" in ready.document.fields && "b" in ready.document.fields)
        assertIs<SchemaMigrator.Outcome.Unsupported>(migrator.migrate(JsonValue.Obj(mapOf("schemaVersion" to JsonValue.Integer(4)))))
        assertIs<SchemaMigrator.Outcome.Unsupported>(migrator.migrate(JsonValue.Obj(emptyMap())))
    }
}
