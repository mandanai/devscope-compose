package dev.chayaphon.journeyscope.export.redaction

import dev.chayaphon.journeyscope.core.model.JourneyTrace

/** Only [RedactionPipeline] can create one, so storage and export never see raw traces. */
public class RedactedTrace internal constructor(
    public val trace: JourneyTrace,
    public val report: RedactionReport,
)

/** Counts of what redaction removed or changed, safe to show to the user before export. */
public data class RedactionReport(
    val droppedKeys: Int = 0,
    val maskedKeys: Int = 0,
    val scrubbedValues: Int = 0,
    val droppedBodies: Int = 0,
    val strippedFailureDetails: Int = 0,
    val includedKeys: Set<String> = emptySet(),
) {
    internal operator fun plus(other: RedactionReport): RedactionReport =
        RedactionReport(
            droppedKeys + other.droppedKeys,
            maskedKeys + other.maskedKeys,
            scrubbedValues + other.scrubbedValues,
            droppedBodies + other.droppedBodies,
            strippedFailureDetails + other.strippedFailureDetails,
            includedKeys + other.includedKeys,
        )

    val totalRedactions: Int get() = droppedKeys + maskedKeys + scrubbedValues + droppedBodies + strippedFailureDetails
}
