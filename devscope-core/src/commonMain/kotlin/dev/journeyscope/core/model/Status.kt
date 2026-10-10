package dev.journeyscope.core.model

import kotlin.jvm.JvmInline

public enum class TraceStatus {
    CREATED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    INTERRUPTED,
    ;

    public val isTerminal: Boolean get() = this != CREATED && this != RUNNING

    internal fun canTransitionTo(target: TraceStatus): Boolean = when (this) {
        CREATED -> target == RUNNING || target == CANCELLED || target == INTERRUPTED
        RUNNING -> target.isTerminal
        else -> false
    }
}

public enum class SpanStatus {
    RUNNING,
    OK,
    FAILED,
    CANCELLED,
    INTERRUPTED,
    ;

    public val isFinished: Boolean get() = this != RUNNING
}

@JvmInline
public value class SpanCategory(public val name: String) {
    override fun toString(): String = name

    public companion object {
        public val UI_INTERACTION: SpanCategory = SpanCategory("UI_INTERACTION")
        public val VIEW_MODEL: SpanCategory = SpanCategory("VIEW_MODEL")
        public val USE_CASE: SpanCategory = SpanCategory("USE_CASE")
        public val REPOSITORY: SpanCategory = SpanCategory("REPOSITORY")
        public val NETWORK: SpanCategory = SpanCategory("NETWORK")
        public val BUSINESS_VALIDATION: SpanCategory = SpanCategory("BUSINESS_VALIDATION")
        public val DATABASE: SpanCategory = SpanCategory("DATABASE")
        public val STATE_EMISSION: SpanCategory = SpanCategory("STATE_EMISSION")
        public val UI_RENDER: SpanCategory = SpanCategory("UI_RENDER")
        public val STATE: SpanCategory = SpanCategory("STATE")
        public val NAVIGATION: SpanCategory = SpanCategory("NAVIGATION")
        public val LIFECYCLE: SpanCategory = SpanCategory("LIFECYCLE")
        public val CUSTOM: SpanCategory = SpanCategory("CUSTOM")
    }
}
