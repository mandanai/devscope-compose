package dev.journeyscope.export.schema

import dev.journeyscope.core.JourneyScope
import dev.journeyscope.core.JourneyScopeConfig

/** A diagnostic finding reduced to non-sensitive fields for inclusion in a report. */
public data class ExportedFinding(
    val ruleId: String,
    val severity: String,
    val title: String,
    val spanId: String? = null,
)

/** Header data written into an exported report. */
public data class ReportContext(
    val platform: String = currentPlatformName(),
    val appVersion: String? = null,
    val sdkVersion: String = JourneyScope.VERSION,
    val exportedAtWallMillis: Long = 0,
    val config: JourneyScopeConfig? = null,
    val findings: Map<String, List<ExportedFinding>> = emptyMap(),
)

internal expect fun currentPlatformName(): String
