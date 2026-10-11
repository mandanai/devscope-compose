package dev.chayaphon.journeyscope.ui

import dev.chayaphon.journeyscope.core.model.TraceId
import dev.chayaphon.journeyscope.core.recorder.InMemoryJourneyRecorder
import dev.chayaphon.journeyscope.export.JourneyExportController
import dev.chayaphon.journeyscope.export.importing.ImportResult
import dev.chayaphon.journeyscope.export.preview.ExportOutcome
import dev.chayaphon.journeyscope.ui.demo.DemoJourneys
import dev.chayaphon.journeyscope.ui.state.ExportDialog
import dev.chayaphon.journeyscope.ui.state.ExportUiState
import dev.chayaphon.journeyscope.ui.state.staticTraceSource
import dev.chayaphon.journeyscope.ui.state.withExport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ExportSourceTest {
    private val live = DemoJourneys.addToCart()

    private fun controller() = JourneyExportController(InMemoryJourneyRecorder())

    @Test
    fun importedTracesAppearBehindLiveOnesAndAreFlagged() =
        runTest {
            val controller = controller()
            val exporter =
                JourneyExportController(
                    InMemoryJourneyRecorder().also { recorder ->
                        recorder.attach(
                            object : dev.chayaphon.journeyscope.core.recorder.RecordedTrace {
                                override val id = live.id
                                override val isFinished = true

                                override fun snapshot() = live

                                override fun interrupt(reason: String) = Unit

                                override fun detach() = Unit
                            },
                        )
                    },
                )
            val report = assertIs<ExportOutcome.Ready>(exporter.export(setOf(live.id.value))).report
            assertIs<ImportResult.Accepted>(controller.importReport(report.json))

            val other = DemoJourneys.addToCart().copy(id = TraceId("live-1"))
            val batch = staticTraceSource(listOf(other), DemoJourneys.NOW_NANOS).withExport(controller).observe().first()

            assertEquals(listOf(live.id.value, "live-1").sorted(), batch.traces.map { it.id.value }.sorted())
            assertEquals(setOf(live.id.value), batch.importedIds)
            assertEquals(
                "live-1",
                batch.traces
                    .last()
                    .id.value,
            )
        }

    @Test
    fun liveTraceWinsOverAnImportedCopyWithTheSameId() =
        runTest {
            val controller = controller()
            val exporter = JourneyExportController(InMemoryJourneyRecorder())
            val json =
                assertIs<ExportOutcome.Ready>(
                    JourneyExportController(InMemoryJourneyRecorder().also { it.attach(finished(live)) }).export(setOf(live.id.value)),
                ).report.json
            controller.importReport(json)
            val batch = staticTraceSource(listOf(live), DemoJourneys.NOW_NANOS).withExport(controller).observe().first()
            assertEquals(1, batch.traces.size)
            assertTrue(batch.importedIds.isEmpty())
            assertTrue(exporter.importedTraces.value.isEmpty())
        }

    @Test
    fun selectionAndDialogStateFollowTheReviewFlow() =
        runTest {
            val recorder = InMemoryJourneyRecorder().also { it.attach(finished(live)) }
            val state = ExportUiState(JourneyExportController(recorder), TestScope(testScheduler))
            state.startSelecting()
            state.toggle(live.id.value)
            assertEquals(setOf(live.id.value), state.selection)
            state.review(state.selection)
            testScheduler.advanceUntilIdle()
            val review = assertIs<ExportDialog.Review>(state.dialog)
            assertEquals(1, review.preview?.traceCount)
            state.requestClear()
            assertIs<ExportDialog.ConfirmClear>(state.dialog)
            state.clear()
            testScheduler.advanceUntilIdle()
            assertTrue(recorder.snapshot().isEmpty())
        }

    private fun finished(trace: dev.chayaphon.journeyscope.core.model.JourneyTrace) =
        object : dev.chayaphon.journeyscope.core.recorder.RecordedTrace {
            override val id = trace.id
            override val isFinished = true

            override fun snapshot() = trace

            override fun interrupt(reason: String) = Unit

            override fun detach() = Unit
        }
}
