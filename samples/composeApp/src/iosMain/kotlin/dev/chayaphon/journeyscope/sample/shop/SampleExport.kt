package dev.chayaphon.journeyscope.sample.shop

import dev.chayaphon.journeyscope.core.JourneyScope
import dev.chayaphon.journeyscope.core.JourneyScopeConfig
import dev.chayaphon.journeyscope.diagnostics.DiagnosticAnalyzer
import dev.chayaphon.journeyscope.export.JourneyExportController
import dev.chayaphon.journeyscope.export.persistence.JourneyPersistence
import dev.chayaphon.journeyscope.export.policy.ExportPolicy
import dev.chayaphon.journeyscope.export.share.ShareSink
import dev.chayaphon.journeyscope.export.storage.createTraceFileSystem
import dev.chayaphon.journeyscope.ui.export.exportFindings

/** Opt-in persistence and export for the sample; stays off until a platform entry point calls [install]. */
object SampleExport {
    private var traceDirectory: String? = null
    private var shareSink: ShareSink? = null

    var controller: JourneyExportController? = null
        private set

    fun install(
        traceDirectory: String,
        shareSink: ShareSink?,
    ) {
        this.traceDirectory = traceDirectory
        this.shareSink = shareSink
    }

    internal fun enable(isDebuggable: Boolean) {
        val config = JourneyScopeConfig.debugOnly(isDebuggable)
        val directory = traceDirectory
        if (directory == null) {
            JourneyScope.configure(config)
            return
        }
        val policy = ExportPolicy(persistenceEnabled = true, appVersion = SAMPLE_VERSION)
        val persistence = JourneyPersistence.install(config, policy, fileSystem = { createTraceFileSystem(directory) })
        JourneyScope.configure(config, persistence.recorder)
        controller =
            JourneyExportController(
                recorder = persistence.recorder,
                store = persistence.store,
                policy = policy,
                shareSink = shareSink,
                findings = DiagnosticAnalyzer().exportFindings(),
            )
    }

    private const val SAMPLE_VERSION = "sample"
}
