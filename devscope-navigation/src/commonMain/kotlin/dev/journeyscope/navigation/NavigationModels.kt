package dev.journeyscope.navigation

import dev.journeyscope.core.Journey

/** Kinds of navigation event recorded under `nav.action`. */
public enum class NavigationAction(
    internal val label: String,
) {
    Navigate("Navigate"),
    Pop("Pop"),
    Replace("Replace"),
    StackSwitch("Stack switch"),
    DestinationEntered("Destination entered"),
    DestinationExited("Destination exited"),
    BackStackSnapshot("Back stack snapshot"),
}

/** Stacks deeper than [maxStackDepth] keep only their last entries. */
public class NavigationTraceConfig(
    /** Maps a route to the text that may be recorded; must never leak argument values. */
    public val routeRedactor: (String) -> String = RouteRedactor.Default,
    public val maxStackDepth: Int = 16,
    /** Evaluated per event when the caller passes no journey. */
    public val journey: (() -> Journey?)? = null,
) {
    init {
        require(maxStackDepth > 0) { "maxStackDepth must be positive" }
    }
}

/** Route-text redaction helpers for [NavigationTraceConfig.routeRedactor]. */
public object RouteRedactor {
    private val hex = Regex("[0-9a-fA-F-]{16,}")

    /** Drops query and fragment, and replaces segments that look like argument values with `{arg}`. */
    public val Default: (String) -> String = { route ->
        val path = route.substringBefore('?').substringBefore('#')
        path.split('/').joinToString("/") { segment -> if (looksLikeValue(segment)) "{arg}" else segment }
    }

    private fun looksLikeValue(segment: String): Boolean =
        segment.isNotEmpty() &&
            !(segment.startsWith("{") && segment.endsWith("}")) &&
            (segment.all { it.isDigit() } || hex.matches(segment) || segment.any { it == '@' || it == '=' } || segment.length > 32)
}
