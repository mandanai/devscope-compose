package dev.journeyscope.ui.model

import androidx.compose.runtime.Immutable

internal enum class StatusKind { Created, Running, Completed, Slow, Failed, Cancelled, Interrupted }

@Immutable
internal data class StatusUi(val kind: StatusKind, val label: String)

internal enum class RowState { Ok, Running, Failed, Cancelled, Interrupted, Event }

@Immutable
internal data class MetadataRowUi(val key: String, val value: String, val hidden: Boolean)

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
internal data class InsightUi(val title: String, val message: String, val targetRowId: String?)

@Immutable
internal data class JourneyDetailUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val status: StatusUi,
    val breakdown: DurationBreakdownUi,
    val rows: List<TimelineRowUi>,
    val insights: List<InsightUi>,
    val failureLabel: String?,
    val droppedItems: Int,
    val sequences: List<SequenceCheckUi> = emptyList(),
)

@Immutable
internal data class SequenceStepUi(val label: String, val observed: Boolean)

@Immutable
internal data class SequenceCheckUi(val name: String, val steps: List<SequenceStepUi>) {
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
)
