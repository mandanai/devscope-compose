package dev.chayaphon.journeyscope.export.redaction

import dev.chayaphon.journeyscope.core.model.TraceMetadataKeys

/** Exact keys, or prefixes written with a trailing `*`. */
public class MetadataAllowlist(
    entries: Set<String>,
) {
    private val exact: Set<String> = entries.filterNot { it.endsWith("*") }.toSet()
    private val prefixes: List<String> = entries.filter { it.endsWith("*") }.map { it.removeSuffix("*") }

    /** Metadata keys not allowed are dropped on export and persistence. */
    public fun allows(key: String): Boolean = key in exact || prefixes.any { key.startsWith(it) }

    public operator fun plus(other: MetadataAllowlist): MetadataAllowlist = MetadataAllowlist(exact + prefixes.map { "$it*" } + other.exact + other.prefixes.map { "$it*" })

    public companion object {
        /** Network, database, navigation, flow and coroutine keys; deliberately excludes free-form details. */
        public val Default: MetadataAllowlist =
            MetadataAllowlist(
                setOf(
                    TraceMetadataKeys.HTTP_METHOD,
                    TraceMetadataKeys.HTTP_HOST,
                    TraceMetadataKeys.HTTP_PATH,
                    TraceMetadataKeys.HTTP_STATUS,
                    TraceMetadataKeys.HTTP_FAILURE,
                    TraceMetadataKeys.BUSINESS_STATUS,
                    TraceMetadataKeys.DB_OPERATION,
                    TraceMetadataKeys.DB_LABEL,
                    TraceMetadataKeys.DB_TRANSACTION_ID,
                    TraceMetadataKeys.DB_TRANSACTION_OUTCOME,
                    TraceMetadataKeys.DB_ROWS,
                    "http.*",
                    "nav.*",
                    "flow.kind",
                    "lifecycle.state",
                    "coroutine.*",
                    "collector.id",
                    "journey.source",
                    "thread",
                    "state.changed",
                    "state.collectors",
                    "state.end",
                    "state.event",
                    "state.stream",
                ),
            )
    }
}
