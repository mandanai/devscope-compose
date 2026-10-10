package dev.journeyscope.core.expectation

import dev.journeyscope.core.model.DiagnosticMetadata
import dev.journeyscope.core.model.JourneyTrace
import dev.journeyscope.core.model.SpanCategory

/** A span start or point event, flattened so steps can match either. */
public class SequenceItem internal constructor(
    public val name: String,
    public val category: SpanCategory,
    public val metadata: DiagnosticMetadata,
    internal val sequence: Long,
)

public class ExpectedStep(
    public val label: String,
    private val matcher: (SequenceItem) -> Boolean,
) {
    internal fun matches(item: SequenceItem): Boolean = matcher(item)

    public companion object {
        public fun named(label: String, namePrefix: String): ExpectedStep =
            ExpectedStep(label) { it.name.startsWith(namePrefix) }

        public fun category(label: String, category: SpanCategory, namePrefix: String = ""): ExpectedStep =
            ExpectedStep(label) { it.category == category && it.name.startsWith(namePrefix) }
    }
}

public data class StepCheck(public val label: String, public val observed: Boolean)

public data class SequenceCheck(public val name: String, public val steps: List<StepCheck>) {
    public val missing: List<String> get() = steps.filterNot { it.observed }.map { it.label }
}

/**
 * An application-declared expectation. Only steps the application listed can be reported missing;
 * a missing step means "not observed by instrumentation", not that the app misbehaved.
 */
public class ExpectedSequence(
    public val name: String,
    public val steps: List<ExpectedStep>,
) {
    public fun evaluate(trace: JourneyTrace): SequenceCheck {
        val items = buildList {
            trace.spans.forEach { add(SequenceItem(it.name, it.category, it.metadata, it.sequence)) }
            trace.events.forEach { add(SequenceItem(it.name, it.category, it.metadata, it.sequence)) }
        }.sortedBy { it.sequence }
        var last = Long.MIN_VALUE
        val checks = steps.map { step ->
            val hit = items.firstOrNull { it.sequence > last && step.matches(it) }
            if (hit != null) last = hit.sequence
            StepCheck(step.label, hit != null)
        }
        return SequenceCheck(name, checks)
    }
}
