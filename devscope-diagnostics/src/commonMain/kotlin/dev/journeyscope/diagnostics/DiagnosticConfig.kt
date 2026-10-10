package dev.journeyscope.diagnostics

import dev.journeyscope.core.expectation.ExpectedSequence
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

public data class DiagnosticConfig(
    val slowJourney: Duration = 2.seconds,
    val slowNetworkSpan: Duration = 800.milliseconds,
    val slowDatabaseOperation: Duration = 100.milliseconds,
    val repeatedRequestMinCount: Int = 3,
    val sequentialDatabaseMinCount: Int = 5,
    val minGap: Duration = 200.milliseconds,
    val gapFractionOfJourney: Double = 0.2,
    val dominantShare: Double = 0.5,
    val databaseSignificantShare: Double = 0.2,
    val expectedSequences: List<ExpectedSequence> = emptyList(),
    val disabledRules: Set<String> = emptySet(),
    val severityOverrides: Map<String, DiagnosticSeverity> = emptyMap(),
    val maxRelatedSpans: Int = 5,
)
