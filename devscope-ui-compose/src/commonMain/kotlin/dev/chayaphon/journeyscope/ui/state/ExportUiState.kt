package dev.chayaphon.journeyscope.ui.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.chayaphon.journeyscope.export.JourneyExportController
import dev.chayaphon.journeyscope.export.importing.ImportResult
import dev.chayaphon.journeyscope.export.preview.ExportOutcome
import dev.chayaphon.journeyscope.export.preview.ExportPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal sealed interface ExportDialog {
    data object None : ExportDialog

    data class Review(
        val ids: Set<String>,
        val preview: ExportPreview?,
        val loading: Boolean,
        val showRaw: Boolean,
    ) : ExportDialog

    data object Import : ExportDialog

    data object ConfirmClear : ExportDialog

    data class Notice(
        val title: String,
        val lines: List<String>,
    ) : ExportDialog
}

@Stable
internal class ExportUiState(
    val controller: JourneyExportController,
    private val scope: CoroutineScope,
) {
    var dialog: ExportDialog by mutableStateOf(ExportDialog.None)
        private set
    var selecting: Boolean by mutableStateOf(false)
        private set
    var selection: Set<String> by mutableStateOf(emptySet())
        private set

    fun startSelecting() {
        selecting = true
        selection = emptySet()
    }

    fun stopSelecting() {
        selecting = false
        selection = emptySet()
    }

    fun toggle(id: String) {
        selection = if (id in selection) selection - id else selection + id
    }

    fun review(ids: Set<String>) {
        dialog = ExportDialog.Review(ids, preview = null, loading = true, showRaw = false)
        scope.launch {
            val preview = controller.preview(ids)
            dialog =
                if (preview == null) {
                    ExportDialog.Notice("Nothing to export", listOf("The selected journeys are no longer available."))
                } else {
                    ExportDialog.Review(ids, preview, loading = false, showRaw = false)
                }
        }
    }

    fun toggleRaw() {
        (dialog as? ExportDialog.Review)?.let { dialog = it.copy(showRaw = !it.showRaw) }
    }

    fun confirmShare() {
        val review = dialog as? ExportDialog.Review ?: return
        scope.launch {
            dialog =
                when (val outcome = controller.export(review.ids)) {
                    is ExportOutcome.Ready -> {
                        if (controller.share(outcome.report)) {
                            stopSelecting()
                            ExportDialog.None
                        } else {
                            ExportDialog.Notice("Could not share", listOf("No share target is available for this report."))
                        }
                    }

                    ExportOutcome.NothingSelected -> {
                        ExportDialog.Notice("Nothing to export", emptyList())
                    }

                    ExportOutcome.TooLarge -> {
                        ExportDialog.Notice("Export too large", listOf("Select fewer journeys and try again."))
                    }
                }
        }
    }

    fun openImport() {
        dialog = ExportDialog.Import
    }

    fun import(text: String) {
        dialog =
            when (val result = controller.importReport(text)) {
                is ImportResult.Accepted -> {
                    ExportDialog.Notice(
                        "Imported ${result.traces.size} journey(s)",
                        listOf("Imported data is untrusted: it is shown read-only and was redacted again.") + result.warnings.take(MAX_LINES),
                    )
                }

                is ImportResult.Rejected -> {
                    ExportDialog.Notice("Import rejected", result.issues.take(MAX_LINES))
                }
            }
    }

    fun requestClear() {
        dialog = ExportDialog.ConfirmClear
    }

    fun clear() {
        dialog = ExportDialog.None
        scope.launch {
            controller.clearHistory()
            stopSelecting()
        }
    }

    fun dismiss() {
        dialog = ExportDialog.None
    }

    private companion object {
        const val MAX_LINES = 8
    }
}
