package dev.journeyscope.export.redaction

import dev.journeyscope.core.RedactionPolicy
import dev.journeyscope.core.model.DiagnosticMetadata
import dev.journeyscope.core.model.FailureInfo
import dev.journeyscope.core.model.JourneyTrace
import dev.journeyscope.core.model.MetadataValue
import dev.journeyscope.export.policy.ExportPolicy

/** Applies the [ExportPolicy] allowlist, sensitive-key masking and value scrubbing; the only way to obtain a [RedactedTrace]. */
public class RedactionPipeline(
    private val policy: ExportPolicy = ExportPolicy.Default,
    private val keyPolicy: RedactionPolicy = RedactionPolicy.Default,
) {
    private val sensitiveFragments: Set<String> =
        (keyPolicy.sensitiveKeyFragments + EXTRA_SENSITIVE_FRAGMENTS)
            .map {
                it.lowercase()
            }.toSet()

    public fun redact(trace: JourneyTrace): RedactedTrace {
        val tally = Tally()
        val spans =
            trace.spans.map { span ->
                span.copy(
                    name = tally.text(span.name),
                    metadata = metadata(span.metadata, tally),
                    failure = failure(span.failure, tally),
                )
            }
        val events = trace.events.map { it.copy(name = tally.text(it.name), metadata = metadata(it.metadata, tally)) }
        val redacted =
            trace.copy(
                name = tally.text(trace.name),
                spans = spans,
                events = events,
                failure = failure(trace.failure, tally),
                metadata = metadata(trace.metadata, tally),
                summary = trace.summary?.let { it.copy(name = tally.text(it.name)) },
            )
        return RedactedTrace(redacted, tally.report())
    }

    private fun metadata(
        source: DiagnosticMetadata,
        tally: Tally,
    ): DiagnosticMetadata {
        if (source.isEmpty) return source
        val kept = LinkedHashMap<String, MetadataValue>()
        for ((key, value) in source.entries) {
            when {
                isBodyKey(key) && !policy.allowBodies -> {
                    tally.droppedBodies++
                }

                !policy.allowlist.allows(key) -> {
                    tally.droppedKeys++
                }

                isSensitive(key) -> {
                    tally.maskedKeys++
                    kept[key] = MetadataValue.Text(ValueScrubber.MASK)
                }

                value is MetadataValue.Text -> {
                    kept[key] = MetadataValue.Text(tally.text(value.value))
                    tally.included += key
                }

                else -> {
                    kept[key] = value
                    tally.included += key
                }
            }
        }
        return DiagnosticMetadata(kept, source.truncated)
    }

    private fun failure(
        source: FailureInfo?,
        tally: Tally,
    ): FailureInfo? {
        if (source == null) return null
        if (source.message != null || source.stackTrace != null) tally.strippedFailureDetails++
        return FailureInfo(source.type, null, null)
    }

    private fun isSensitive(key: String): Boolean {
        val lowered = key.lowercase()
        return sensitiveFragments.any { lowered.contains(it) }
    }

    private fun isBodyKey(key: String): Boolean {
        val lowered = key.lowercase()
        return lowered.endsWith(".body") || lowered.contains(".body.") || lowered == "body" || lowered.endsWith("payload")
    }

    private class Tally {
        var droppedKeys = 0
        var maskedKeys = 0
        var scrubbed = 0
        var droppedBodies = 0
        var strippedFailureDetails = 0
        val included = LinkedHashSet<String>()

        fun text(value: String): String {
            val result = ValueScrubber.scrub(value)
            if (result.changed) scrubbed++
            return result.text
        }

        fun report(): RedactionReport = RedactionReport(droppedKeys, maskedKeys, scrubbed, droppedBodies, strippedFailureDetails, included.toSet())
    }

    private companion object {
        val EXTRA_SENSITIVE_FRAGMENTS = setOf("otp", "refresh", "passcode", "email", "phone", "signature", "x-amz", "bearer", "jwt")
    }
}
