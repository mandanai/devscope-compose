package dev.chayaphon.journeyscope.android

import android.content.Context
import androidx.core.content.FileProvider
import dev.chayaphon.journeyscope.core.JourneyScope
import dev.chayaphon.journeyscope.export.JourneyExportController
import dev.chayaphon.journeyscope.export.share.AndroidShareSink

internal object DefaultExport {
    fun controller(context: Context): JourneyExportController {
        val authority = "${context.packageName}.journeyscope.fileprovider"
        return JourneyExportController(
            recorder = JourneyScope.tracer.recorder,
            shareSink = AndroidShareSink(context, uriFor = { FileProvider.getUriForFile(context, authority, it) }),
        )
    }
}
