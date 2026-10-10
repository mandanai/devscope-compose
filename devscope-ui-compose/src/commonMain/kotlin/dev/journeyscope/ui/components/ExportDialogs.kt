package dev.journeyscope.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.journeyscope.export.preview.ExportPreview
import dev.journeyscope.ui.state.ExportDialog
import dev.journeyscope.ui.state.ExportUiState
import dev.journeyscope.ui.theme.JourneyTheme

private const val RAW_PREVIEW_CHARS = 4_000

@Composable
internal fun ExportDialogHost(state: ExportUiState) {
    when (val dialog = state.dialog) {
        ExportDialog.None -> Unit
        is ExportDialog.Review -> ReviewDialog(dialog, canShare = state.controller.canShare, state = state)
        ExportDialog.Import -> ImportDialog(onImport = state::import, onDismiss = state::dismiss)
        ExportDialog.ConfirmClear -> AlertDialog(
            onDismissRequest = state::dismiss,
            title = { Text("Clear history?") },
            text = { Text("Removes recorded, saved and imported journeys from this device. This cannot be undone.") },
            confirmButton = { TextButton(onClick = state::clear) { Text("Clear") } },
            dismissButton = { TextButton(onClick = state::dismiss) { Text("Cancel") } },
        )
        is ExportDialog.Notice -> AlertDialog(
            onDismissRequest = state::dismiss,
            title = { Text(dialog.title) },
            text = { LineList(dialog.lines) },
            confirmButton = { TextButton(onClick = state::dismiss) { Text("OK") } },
        )
    }
}

@Composable
private fun ReviewDialog(dialog: ExportDialog.Review, canShare: Boolean, state: ExportUiState) {
    val preview = dialog.preview
    AlertDialog(
        onDismissRequest = state::dismiss,
        title = { Text("Review before sharing") },
        text = {
            if (preview == null) {
                Text("Preparing a redacted copy…")
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreviewSummary(preview)
                    TextButton(onClick = state::toggleRaw) { Text(if (dialog.showRaw) "Hide report contents" else "Show report contents") }
                    if (dialog.showRaw) {
                        Text(
                            preview.json.take(RAW_PREVIEW_CHARS) + if (preview.json.length > RAW_PREVIEW_CHARS) "\n… (truncated for review)" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = JourneyTheme.colors.textSecondary,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = state::confirmShare, enabled = preview != null && canShare && !preview.exceedsLimits) {
                Text(if (canShare) "Share" else "Sharing unavailable")
            }
        },
        dismissButton = { TextButton(onClick = state::dismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PreviewSummary(preview: ExportPreview) {
    val colors = JourneyTheme.colors
    val redaction = preview.redaction
    val lines = buildList {
        add("${preview.traceCount} journey(s), ${preview.spanCount} spans, ${preview.eventCount} events")
        add("Size: ${preview.sizeBytes / BYTES_PER_KB + 1} KB")
        add("Fields removed or masked: ${redaction.totalRedactions}")
        add("Request/response bodies: excluded")
        if (preview.includedMetadataKeys.isNotEmpty()) add("Metadata included: ${preview.includedMetadataKeys.joinToString()}")
        if (preview.exceedsLimits) add("This selection exceeds the export limits; choose fewer journeys.")
        add("Nothing is sent anywhere until you pick a destination.")
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary) }
    }
}

@Composable
private fun ImportDialog(onImport: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import journey report") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Paste a JourneyScope report. It is validated and treated as untrusted data.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp), label = { Text("Report JSON") })
            }
        },
        confirmButton = { TextButton(onClick = { onImport(text) }, enabled = text.isNotBlank()) { Text("Import") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LineList(lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

private const val BYTES_PER_KB = 1024
