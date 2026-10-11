package dev.chayaphon.journeyscope.ui.model

import androidx.compose.runtime.Immutable
import dev.chayaphon.journeyscope.diagnostics.DiagnosticSeverity

internal enum class StatusKind { Created, Running, Completed, Slow, Failed, Cancelled, Interrupted }

@Immutable
internal data class StatusUi(
    val kind: StatusKind,
    val label: String,
)

internal enum class RowState { Ok, Running, Failed, Cancelled, Interrupted, Event }

@Immutable
internal data class MetadataRowUi(
    val key: String,
    val value: String,
    val hidden: Boolean,
)

@Immutable
internal data class TimelineRowUi(
    val id: String,
    val title: String,
    val detail: String,
    val category: JourneyCategory,
    val glyph: Glyph,
    val durationLabel: String,
    val selfNanos: Long,
    val offsetLabel: String,
    val state: RowState,
    val isBottleneck: Boolean,
    val depth: Int,
    val selfLabel: String?,
    val failureLabel: String?,
    val metadata: List<MetadataRowUi>,
    val spokenDescription: String,
)

@Immutable
internal data class DurationSegmentUi(
    val category: JourneyCategory,
    val nanos: Long,
    val label: String,
    val percent: Int,
    val weight: Float,
)

@Immutable
internal data class DurationBreakdownUi(
    val totalNanos: Long,
    val totalLabel: String,
    val segments: List<DurationSegmentUi>,
    val spokenDescription: String,
)

@Immutable
internal data class RelatedRowUi(
    val rowId: String,
    val label: String,
)

@Immutable
internal data class DiagnosticUi(
    val ruleId: String,
    val severity: DiagnosticSeverity,
    val categoryLabel: String,
    val title: String,
    val evidence: List<String>,
    val inference: String?,
    val nextSteps: List<String>,
    val related: List<RelatedRowUi>,
    val metadata: List<MetadataRowUi>,
)

@Immutable
internal data class TimingRowUi(
    val category: JourneyCategory,
    val attributedLabel: String,
    val percentLabel: String,
)

@Immutable
internal data class TimingBreakdownUi(
    val totalLabel: String,
    val rows: List<TimingRowUi>,
    val overlapNote: String?,
    val slowestLabel: String?,
    val criticalPathLabel: String,
)

@Immutable
internal data class JourneyDetailUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val status: StatusUi,
    val breakdown: DurationBreakdownUi,
    val rows: List<TimelineRowUi>,
    val diagnostics: List<DiagnosticUi>,
    val timing: TimingBreakdownUi,
    val failureLabel: String?,
    val droppedItems: Int,
    val sequences: List<SequenceCheckUi> = emptyList(),
)

@Immutable
internal data class SequenceStepUi(
    val label: String,
    val observed: Boolean,
)

@Immutable
internal data class SequenceCheckUi(
    val name: String,
    val steps: List<SequenceStepUi>,
) {
    val missingCount: Int get() = steps.count { !it.observed }
}

@Immutable
internal data class JourneyListItemUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val status: StatusUi,
    val breakdown: DurationBreakdownUi,
    val spokenDescription: String,
    val imported: Boolean = false,
)
