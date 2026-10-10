package dev.journeyscope.flow

import dev.journeyscope.core.Journey

/** Turns an observed value into a short, safe summary. Emitted objects are never serialized implicitly. */
public fun interface ValueFormatter<in T> {
    public fun format(value: T): String

    public companion object {
        /** Reports only the value's simple type name. */
        public val TypeName: ValueFormatter<Any?> = ValueFormatter { value ->
            if (value == null) "null" else value::class.simpleName ?: "value"
        }
    }
}

/** Where a collector's lifecycle comes from; platform adapters implement this outside commonMain. */
public fun interface LifecycleSource {
    public fun states(): kotlinx.coroutines.flow.Flow<CollectorLifecycleState>
}

public enum class CollectorLifecycleState { Created, Started, Resumed, Stopped, Destroyed }

public class FlowTraceOptions<T>(
    public val formatter: ValueFormatter<T> = ValueFormatter.TypeName,
    public val maxSummaryLength: Int = 80,
    public val recordUpstream: Boolean = true,
    public val recordObserved: Boolean = true,
    /** Evaluated per event; used when the collecting coroutine carries no ambient journey. */
    public val journey: (() -> Journey?)? = null,
    public val lifecycle: LifecycleSource? = null,
) {
    init {
        require(maxSummaryLength > 0) { "maxSummaryLength must be positive" }
    }
}
