package dev.journeyscope.ui.mapping

/**
 * Decides which metadata values the inspector may render. Core already redacts at capture time;
 * this is a second, UI-side gate. A key ending in `*` matches by prefix.
 */
public class MetadataDisplayPolicy(
    public val visibleKeys: Set<String> = DEFAULT_VISIBLE_KEYS,
    public val showAll: Boolean = false,
    public val showFailureMessages: Boolean = false,
) {
    public fun isVisible(key: String): Boolean = showAll || visibleKeys.any { rule ->
        if (rule.endsWith("*")) key.startsWith(rule.dropLast(1)) else key == rule
    }

    public companion object {
        public val DEFAULT_VISIBLE_KEYS: Set<String> =
            setOf(
                DETAIL_KEY, SOURCE_KEY, "http.method", "http.host", "http.status", "http.path", "http.failure", "http.protocol",
                "http.streaming", "http.request.bytes", "http.response.bytes", "http.request.header.*", "http.response.header.*",
                "business.status", "returnCode", "component", "state.*", "flow.kind", "collector.id", "lifecycle.state", "nav.*",
                "coroutine.*", UI_CHECKPOINT_KEY,
                "db.operation", "db.label", "db.transaction.*", "db.rows",
            )

        public val Default: MetadataDisplayPolicy = MetadataDisplayPolicy()

        public val ShowAll: MetadataDisplayPolicy = MetadataDisplayPolicy(showAll = true, showFailureMessages = true)

        public const val DETAIL_KEY: String = "detail"
        public const val SOURCE_KEY: String = "journey.source"
        public const val UI_CHECKPOINT_KEY: String = "ui.checkpoint"
    }
}
