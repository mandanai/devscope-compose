package dev.journeyscope.navigation

import dev.journeyscope.core.Journey
import dev.journeyscope.core.model.DiagnosticMetadata
import dev.journeyscope.core.model.SpanCategory
import dev.journeyscope.core.model.diagnosticMetadata

/**
 * Framework-neutral navigation recorder. The host calls it from its navigation controller or state owner;
 * nothing is observed automatically. Routes are redacted and only route templates should be passed, never arguments.
 */
public class NavigationTracer(
    private val config: NavigationTraceConfig = NavigationTraceConfig(),
) {
    /** Return values are true only when an event was recorded; false means no recording journey was available or recording failed, and never throws. */
    public fun navigate(
        from: String?,
        to: String,
        backStackBefore: List<String>,
        backStackAfter: List<String>,
        journey: Journey? = null,
    ): Boolean = emit(NavigationAction.Navigate, from, to, backStackBefore, backStackAfter, journey)

    /** Records a back navigation; [to] is null when the stack was emptied. */
    public fun pop(
        from: String?,
        to: String?,
        backStackBefore: List<String>,
        backStackAfter: List<String>,
        journey: Journey? = null,
    ): Boolean = emit(NavigationAction.Pop, from, to, backStackBefore, backStackAfter, journey)

    /** Records the current destination being replaced. */
    public fun replace(
        from: String?,
        to: String,
        backStackBefore: List<String>,
        backStackAfter: List<String>,
        journey: Journey? = null,
    ): Boolean = emit(NavigationAction.Replace, from, to, backStackBefore, backStackAfter, journey)

    /** Records a switch between top-level stacks, e.g. bottom-bar tabs. */
    public fun switchStack(
        fromStack: String,
        toStack: String,
        backStackBefore: List<String>,
        backStackAfter: List<String>,
        journey: Journey? = null,
    ): Boolean = emit(NavigationAction.StackSwitch, fromStack, toStack, backStackBefore, backStackAfter, journey)

    /** Records a destination becoming visible. */
    public fun destinationEntered(
        route: String,
        backStack: List<String>,
        journey: Journey? = null,
    ): Boolean = emit(NavigationAction.DestinationEntered, null, route, null, backStack, journey)

    /** Records a destination leaving the screen. */
    public fun destinationExited(
        route: String,
        backStack: List<String>,
        journey: Journey? = null,
    ): Boolean = emit(NavigationAction.DestinationExited, route, null, backStack, null, journey)

    /** Records the current back stack without a transition. */
    public fun snapshot(
        backStack: List<String>,
        journey: Journey? = null,
    ): Boolean = emit(NavigationAction.BackStackSnapshot, null, null, null, backStack, journey)

    /** The explicit "navigation finished" checkpoint, e.g. the destination's first frame; never inferred. */
    public fun markCompleted(
        destination: String,
        journey: Journey? = null,
    ): Boolean {
        val target = resolve(journey) ?: return false
        return try {
            val route = config.routeRedactor(destination)
            target.recordEvent(
                "Navigation completed: $route",
                SpanCategory.NAVIGATION,
                diagnosticMetadata {
                    put("nav.action", "completed")
                    put("nav.to", route)
                    put("nav.completed", true)
                    put("nav.trace", target.id.value)
                    put("detail", route)
                },
            )
            true
        } catch (ignored: Throwable) {
            false
        }
    }

    private fun emit(
        action: NavigationAction,
        from: String?,
        to: String?,
        before: List<String>?,
        after: List<String>?,
        journey: Journey?,
    ): Boolean {
        val target = resolve(journey) ?: return false
        return try {
            val fromRoute = from?.let(config.routeRedactor)
            val toRoute = to?.let(config.routeRedactor)
            val stackBefore = before?.let(::redactStack)
            val stackAfter = after?.let(::redactStack)
            target.recordEvent(
                titleOf(action, fromRoute, toRoute),
                SpanCategory.NAVIGATION,
                metadataOf(action, fromRoute, toRoute, stackBefore, stackAfter, target.id.value),
            )
            true
        } catch (ignored: Throwable) {
            false
        }
    }

    private fun resolve(explicit: Journey?): Journey? = (explicit ?: runCatching { config.journey?.invoke() }.getOrNull())?.takeIf { it.isRecording }

    private fun redactStack(stack: List<String>): List<String> = stack.takeLast(config.maxStackDepth).map(config.routeRedactor)

    private fun titleOf(
        action: NavigationAction,
        from: String?,
        to: String?,
    ): String =
        when (action) {
            NavigationAction.BackStackSnapshot -> action.label
            NavigationAction.DestinationEntered -> "${action.label}: $to"
            NavigationAction.DestinationExited -> "${action.label}: $from"
            else -> "${action.label}: ${from ?: "∅"} → ${to ?: "∅"}"
        }

    private fun metadataOf(
        action: NavigationAction,
        from: String?,
        to: String?,
        before: List<String>?,
        after: List<String>?,
        traceId: String,
    ): DiagnosticMetadata =
        diagnosticMetadata {
            put("nav.action", action.name)
            from?.let { put("nav.from", it) }
            to?.let { put("nav.to", it) }
            before?.let {
                put("nav.stack.before", it.joinToString(" > "))
                put("nav.depth.before", it.size)
            }
            after?.let {
                put("nav.stack.after", it.joinToString(" > "))
                put("nav.depth.after", it.size)
            }
            put("nav.trace", traceId)
            put("detail", detailOf(action, from, to, before, after))
        }

    private fun detailOf(
        action: NavigationAction,
        from: String?,
        to: String?,
        before: List<String>?,
        after: List<String>?,
    ): String =
        when (action) {
            NavigationAction.BackStackSnapshot -> after?.joinToString(" > ").orEmpty()
            NavigationAction.DestinationEntered -> to.orEmpty()
            NavigationAction.DestinationExited -> from.orEmpty()
            else -> "${from ?: "∅"} → ${to ?: "∅"} · stack ${before?.size ?: 0} → ${after?.size ?: 0}"
        }
}
