package dev.chayaphon.journeyscope.export.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.chayaphon.journeyscope.export.schema.TraceSchema
import java.io.File

/**
 * Shares through the system chooser. Small reports go as text; larger ones need [uriFor] so the host can
 * expose the file through its own FileProvider (the library ships no manifest entries).
 */
public class AndroidShareSink(
    private val context: Context,
    private val uriFor: ((File) -> Uri)? = null,
    private val maxInlineTextBytes: Int = DEFAULT_MAX_INLINE_BYTES,
) : ShareSink {
    override suspend fun share(report: ExportedReport): Boolean {
        val send = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_SUBJECT, report.fileName)
        val factory = uriFor
        when {
            factory != null -> {
                val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
                val file = File(dir, report.fileName).apply { writeText(report.json, Charsets.UTF_8) }
                send
                    .setType(TraceSchema.MIME_TYPE)
                    .putExtra(Intent.EXTRA_STREAM, factory(file))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            report.sizeBytes <= maxInlineTextBytes -> {
                send.setType("text/plain").putExtra(Intent.EXTRA_TEXT, report.json)
            }

            else -> {
                return false
            }
        }
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    private companion object {
        const val EXPORT_DIR = "journeyscope-exports"
        const val DEFAULT_MAX_INLINE_BYTES = 200 * 1024
    }
}
