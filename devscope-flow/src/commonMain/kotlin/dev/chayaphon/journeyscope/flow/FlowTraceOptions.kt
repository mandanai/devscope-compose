package dev.chayaphon.journeyscope.flow

import dev.chayaphon.journeyscope.core.Journey

/** Turns an observed value into a short, safe summary. Emitted objects are never serialized implicitly. */
public fun interface ValueFormatter<in T> {
    public fun format(value: T): String

    public companion object {
        /** Reports only the value's simple type name. */
        public val TypeName: ValueFormatter<Any?> =
            ValueFormatter { value ->
                if (value == null) "null" else value::class.simpleName ?: "value"
            }
    }
}

/** Where a collector's lifecycle comes from; platform adapters implement this outside commonMain. */
public fun interface LifecycleSource {
    public fun states(): kotlinx.coroutines.flow.Flow<CollectorLifecycleState>
}

/** Platform-neutral lifecycle of a collector's owner, mirroring Android's Lifecycle states. */
public enum class CollectorLifecycleState { Created, Started, Resumed, Stopped, Destroyed }

/** Controls what [traceFlow], [traceState] and [traceShared] record; values are summarized, never serialized. */
public class FlowTraceOptions<T>(
    /** A formatter that throws is recorded as `<unformattable>` and never breaks the flow. */
    public val formatter: ValueFormatter<T> = ValueFormatter.TypeName,
    /** Longest formatted summary recorded; longer text is truncated. */
    public val maxSummaryLength: Int = 80,
    /** Record values as they leave the source, before any downstream delay. */
    public val recordUpstream: Boolean = true,
    /** Record values once the downstream collector has accepted them. */
    public val recordObserved: Boolean = true,
    /** Evaluated per event; used when the collecting coroutine carries no ambient journey. */
    public val journey: (() -> Journey?)? = null,
    /** When set, the owner's lifecycle changes are recorded alongside collection events. */
    public val lifecycle: LifecycleSource? = null,
) {
    init {
        require(maxSummaryLength > 0) { "maxSummaryLength must be positive" }
    }
}
