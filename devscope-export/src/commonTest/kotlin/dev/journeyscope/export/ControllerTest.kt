package dev.journeyscope.export

import dev.journeyscope.core.recorder.InMemoryJourneyRecorder
import dev.journeyscope.core.recorder.RecordedTrace
import dev.journeyscope.export.importing.ImportResult
import dev.journeyscope.export.policy.ExportPolicy
import dev.journeyscope.export.preview.ExportOutcome
import dev.journeyscope.export.share.ShareSink
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ControllerTest {
    private val trace = sampleTrace()
    private val recorder = InMemoryJourneyRecorder().also { it.attach(Finished(trace)) }

    private class Finished(
        private val trace: dev.journeyscope.core.model.JourneyTrace,
    ) : RecordedTrace {
        override val id = trace.id
        override val isFinished = true

        override fun snapshot() = trace

        override fun interrupt(reason: String) = Unit

        override fun detach() = Unit
    }

    @Test
    fun previewReportsCountsAndRedactionWithoutSharing() =
        runTest {
            var shared = 0
            val controller =
                JourneyExportController(
                    recorder,
                    shareSink =
                        ShareSink {
                            shared++
                            true
                        },
                )
            val preview = assertNotNull(controller.preview(setOf(trace.id.value)))
            assertEquals(1, preview.traceCount)
            assertEquals(trace.spans.size, preview.spanCount)
            assertTrue(preview.redaction.droppedKeys > 0)
            assertFalse(preview.json.contains("abcdefghijklmnop"))
            assertEquals(0, shared)
        }

    @Test
    fun unknownSelectionYieldsNothing() =
        runTest {
            val controller = JourneyExportController(recorder)
            assertNull(controller.preview(setOf("missing")))
            assertIs<ExportOutcome.NothingSelected>(controller.export(emptySet()))
        }

    @Test
    fun exportRespectsByteLimitsAndSharingNeedsASink() =
        runTest {
            val tight = JourneyExportController(recorder, policy = ExportPolicy(maxExportBytes = 100))
            assertIs<ExportOutcome.TooLarge>(tight.export(setOf(trace.id.value)))
            val controller = JourneyExportController(recorder)
            val ready = assertIs<ExportOutcome.Ready>(controller.export(setOf(trace.id.value)))
            assertFalse(controller.canShare)
            assertFalse(controller.share(ready.report))
        }

    @Test
    fun importedReportsAreKeptSeparateFromTheRecorder() =
        runTest {
            val controller = JourneyExportController(recorder)
            val json = assertIs<ExportOutcome.Ready>(controller.export(setOf(trace.id.value))).report.json
            val other = JourneyExportController(InMemoryJourneyRecorder())
            assertIs<ImportResult.Accepted>(other.importReport(json))
            assertEquals(1, other.importedTraces.value.size)
            assertIs<ImportResult.Rejected>(other.importReport("{}"))
            assertEquals(0, InMemoryJourneyRecorder().snapshot().size)
        }

    @Test
    fun clearHistoryEmptiesRecorderAndImports() =
        runTest {
            val controller = JourneyExportController(recorder)
            controller.clearHistory()
            assertTrue(recorder.snapshot().isEmpty())
        }
}
