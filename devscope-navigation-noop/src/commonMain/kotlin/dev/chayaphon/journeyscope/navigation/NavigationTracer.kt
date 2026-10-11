package dev.chayaphon.journeyscope.navigation

import dev.chayaphon.journeyscope.core.Journey

/** Release-build twin of the navigation recorder: every call is accepted and reports that nothing was recorded. */
public class NavigationTracer(
    private val config: NavigationTraceConfig = NavigationTraceConfig(),
) {
    public fun navigate(
        from: String?,
        to: String,
        backStackBefore: List<String>,
        backStackAfter: List<String>,
        journey: Journey? = null,
    ): Boolean = false

    public fun pop(
        from: String?,
        to: String?,
        backStackBefore: List<String>,
        backStackAfter: List<String>,
        journey: Journey? = null,
    ): Boolean = false

    public fun replace(
        from: String?,
        to: String,
        backStackBefore: List<String>,
        backStackAfter: List<String>,
        journey: Journey? = null,
    ): Boolean = false

    public fun switchStack(
        fromStack: String,
        toStack: String,
        backStackBefore: List<String>,
        backStackAfter: List<String>,
        journey: Journey? = null,
    ): Boolean = false

    public fun destinationEntered(
        route: String,
        backStack: List<String>,
        journey: Journey? = null,
    ): Boolean = false

    public fun destinationExited(
        route: String,
        backStack: List<String>,
        journey: Journey? = null,
    ): Boolean = false

    public fun snapshot(
        backStack: List<String>,
        journey: Journey? = null,
    ): Boolean = false

    public fun markCompleted(
        destination: String,
        journey: Journey? = null,
    ): Boolean = false
}
