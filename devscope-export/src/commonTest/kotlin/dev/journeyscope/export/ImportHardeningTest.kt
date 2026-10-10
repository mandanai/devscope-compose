package dev.journeyscope.export

import dev.journeyscope.export.importing.ImportLimits
import dev.journeyscope.export.importing.ImportResult
import dev.journeyscope.export.importing.TraceImporter
import dev.journeyscope.export.redaction.RedactionPipeline
import dev.journeyscope.export.schema.TraceReportWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ImportHardeningTest {
    private val valid = TraceReportWriter.write(listOf(RedactionPipeline().redact(sampleTrace())))

    private fun rejected(text: String, limits: ImportLimits = ImportLimits()): List<String> =
        assertIs<ImportResult.Rejected>(TraceImporter(limits).import(text)).issues

    @Test
    fun rejectsMalformedAndTruncatedJson() {
        assertTrue(rejected("{not json").single().startsWith("malformed JSON"))
        assertTrue(rejected(valid.dropLast(10)).single().startsWith("malformed JSON"))
        assertTrue(rejected("[]").isNotEmpty())
    }

    @Test
    fun rejectsOversizedDocumentsBeforeParsing() {
        val issues = rejected(valid, ImportLimits(maxBytes = 100))
        assertTrue(issues.single().contains("exceeds"))
    }

    @Test
    fun rejectsDeepNestingAndHugeStrings() {
        assertTrue(rejected("[".repeat(200) + "]".repeat(200)).single().contains("nesting"))
        val longName = valid.replace("\"checkout\"", "\"" + "x".repeat(5000) + "\"")
        assertTrue(rejected(longName, ImportLimits(maxStringLength = 512)).isNotEmpty())
    }

    @Test
    fun rejectsUnknownOrNewerSchemaVersions() {
        assertTrue(rejected(valid.replace("\"schemaVersion\":1", "\"schemaVersion\":99")).single().contains("newer"))
        assertTrue(rejected(valid.replace("journeyscope.trace", "other.format")).isNotEmpty())
    }

    @Test
    fun rejectsDanglingParentsAndCycles() {
        val trace = RedactionPipeline().redact(sampleTrace()).trace
        val child = trace.spans.first { it.parentId != null }
        val dangling = valid.replace("\"parentId\":\"${child.parentId}\"", "\"parentId\":\"ghost\"")
        assertTrue(rejected(dangling).any { it.contains("missing parent") })

        val root = trace.spans.first { it.parentId == null }
        val cyclic = valid.replace(
            "\"id\":\"${root.id}\",",
            "\"id\":\"${root.id}\",\"parentId\":\"${child.id}\",",
        )
        assertTrue(rejected(cyclic).any { it.contains("cycle") || it.contains("starts before") })
    }

    @Test
    fun rejectsEventsReferencingMissingSpansAndDuplicateIds() {
        val trace = RedactionPipeline().redact(sampleTrace()).trace
        val event = trace.events.first()
        val withSpan = valid.replace("\"id\":\"${event.id}\"", "\"id\":\"${event.id}\",\"spanId\":\"nope\"")
        assertTrue(rejected(withSpan).any { it.contains("missing span") })
        val duplicate = valid.replace("\"id\":\"${trace.spans[1].id}\"", "\"id\":\"${trace.spans[0].id}\"")
        assertTrue(rejected(duplicate).any { it.contains("duplicate span id") })
    }

    @Test
    fun enforcesItemCountsAndBadFieldValues() {
        assertTrue(rejected(valid, ImportLimits(maxSpansPerTrace = 1)).any { it.contains("more than 1") })
        assertTrue(rejected(valid.replace("\"status\":\"COMPLETED\"", "\"status\":\"HACKED\"")).isNotEmpty())
        assertTrue(rejected(valid.replace("\"sequence\":0", "\"sequence\":-5")).isNotEmpty())
    }

    @Test
    fun unknownFieldsAreWarnedInLenientModeAndRejectedInStrictMode() {
        val extended = valid.replace("\"sdkVersion\"", "\"surprise\":1,\"sdkVersion\"")
        val accepted = assertIs<ImportResult.Accepted>(TraceImporter().import(extended))
        assertTrue(accepted.warnings.any { it.contains("surprise") })
        assertTrue(rejected(extended, ImportLimits(strict = true)).any { it.contains("surprise") })
    }

    @Test
    fun importedContentIsRedactedAgain() {
        val hostile = valid.replace("\"http.method\":\"POST\"", "\"http.method\":\"POST\",\"password\":\"hunter2\"")
        val trace = assertIs<ImportResult.Accepted>(TraceImporter().import(hostile)).traces.single()
        assertEquals(null, trace.spans.firstNotNullOfOrNull { it.metadata["password"] })
    }
}
