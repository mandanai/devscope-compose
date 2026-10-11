package dev.chayaphon.journeyscope.export.policy

import dev.chayaphon.journeyscope.export.redaction.MetadataAllowlist
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/** Privacy and size limits for persistence and export. Defaults keep persistence off and bodies out of reports. */
public data class ExportPolicy(
    val persistenceEnabled: Boolean = false,
    val allowBodies: Boolean = false,
    val allowlist: MetadataAllowlist = MetadataAllowlist.Default,
    val retention: Duration = 7.days,
    val maxStoredTraces: Int = 100,
    val maxTotalBytes: Long = 8L * 1024 * 1024,
    val maxTraceBytes: Long = 512L * 1024,
    val queueCapacity: Int = 32,
    val maxExportTraces: Int = 50,
    val maxExportBytes: Long = 4L * 1024 * 1024,
    val appVersion: String? = null,
) {
    init {
        require(retention.isPositive()) { "retention must be positive" }
        require(maxStoredTraces > 0) { "maxStoredTraces must be positive" }
        require(maxTotalBytes > 0) { "maxTotalBytes must be positive" }
        require(maxTraceBytes in 1..maxTotalBytes) { "maxTraceBytes must be within 1..maxTotalBytes" }
        require(queueCapacity > 0) { "queueCapacity must be positive" }
        require(maxExportTraces > 0) { "maxExportTraces must be positive" }
        require(maxExportBytes > 0) { "maxExportBytes must be positive" }
    }

    public companion object {
        public val Default: ExportPolicy = ExportPolicy()
    }
}
