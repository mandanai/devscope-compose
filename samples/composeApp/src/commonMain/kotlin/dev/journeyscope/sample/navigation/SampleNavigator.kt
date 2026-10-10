package dev.journeyscope.sample.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import dev.journeyscope.core.Journey
import dev.journeyscope.navigation.NavigationTraceConfig
import dev.journeyscope.navigation.NavigationTracer

object Routes {
    const val PRODUCT = "product"
    const val CART = "cart"
    const val INSPECTOR_LIVE = "inspector/live"
    const val INSPECTOR_DEMO = "inspector/demo"
}

/** Minimal back stack that reports every transition to [NavigationTracer]. */
class SampleNavigator(
    private val journeyProvider: () -> Journey?,
    initialStack: List<String> = listOf(Routes.PRODUCT),
) {
    private val tracer = NavigationTracer(NavigationTraceConfig(journey = journeyProvider))

    var stack: List<String> by mutableStateOf(initialStack)
        private set

    val current: String get() = stack.last()

    fun push(route: String, journey: Journey? = null) {
        val before = stack
        stack = before + route
        tracer.navigate(before.last(), route, before, stack, journey)
        tracer.destinationEntered(route, stack, journey)
    }

    fun pop(journey: Journey? = null) {
        val before = stack
        if (before.size <= 1) return
        stack = before.dropLast(1)
        tracer.pop(before.last(), stack.last(), before, stack, journey)
    }

    fun markShown(route: String, journey: Journey?) {
        tracer.markCompleted(route, journey)
    }

    companion object {
        fun saver(journeyProvider: () -> Journey?): Saver<SampleNavigator, Any> = listSaver(
            save = { it.stack },
            restore = { SampleNavigator(journeyProvider, it) },
        )
    }
}
