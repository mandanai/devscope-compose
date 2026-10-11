package dev.chayaphon.journeyscope.diagnostics

import dev.chayaphon.journeyscope.core.expectation.ExpectedSequence
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Thresholds and rule switches for [DiagnosticAnalyzer]; durations are inclusive lower bounds and shares are fractions in 0..1. */
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
    /** Replaces a rule's severity by rule id. */
    val severityOverrides: Map<String, DiagnosticSeverity> = emptyMap(),
    /** Caps the span ids attached to a single finding. */
    val maxRelatedSpans: Int = 5,
)
