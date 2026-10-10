package dev.journeyscope.sample

import dev.journeyscope.core.JourneyScope
import dev.journeyscope.core.JourneyScopeConfig
import dev.journeyscope.core.model.SpanCategory
import kotlinx.coroutines.delay

fun enableJourneyScopeForSample() {
    if (!JourneyScope.isEnabled) JourneyScope.configure(JourneyScopeConfig.debugOnly(isDebuggable = true))
}

suspend fun runAddToCartJourney() {
    JourneyScope.withJourney("Add to Cart") {
        recordEvent("Button Click", SpanCategory.UI_INTERACTION)
        traceSuspend("AddToCartUseCase", SpanCategory.USE_CASE) {
            traceSuspend("POST /cart/items", SpanCategory.NETWORK) { delay(NETWORK_LATENCY_MS) }
            traceSuspend("CartRepository.add", SpanCategory.REPOSITORY) { delay(REPOSITORY_LATENCY_MS) }
        }
        recordEvent("CartUiState.Success", SpanCategory.STATE_EMISSION)
        traceSuspend("Cart count render", SpanCategory.UI_RENDER) { delay(RENDER_LATENCY_MS) }
    }
}

private const val NETWORK_LATENCY_MS = 900L
private const val REPOSITORY_LATENCY_MS = 40L
private const val RENDER_LATENCY_MS = 30L
