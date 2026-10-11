package dev.chayaphon.journeyscope.sample.shop

import dev.chayaphon.journeyscope.core.currentTraceContext
import dev.chayaphon.journeyscope.core.model.DiagnosticMetadata
import dev.chayaphon.journeyscope.core.model.SpanCategory
import dev.chayaphon.journeyscope.core.model.diagnosticMetadata

/** Joins the journey carried by the coroutine context, so no layer needs a `Journey` parameter. */
suspend fun <T> traced(
    name: String,
    category: SpanCategory,
    metadata: DiagnosticMetadata = DiagnosticMetadata.Empty,
    block: suspend () -> T,
): T {
    val journey = currentTraceContext()?.journey ?: return block()
    return journey.traceSuspend(name, category, metadata, block)
}

internal fun simulatedMetadata(vararg extra: Pair<String, String>): DiagnosticMetadata =
    diagnosticMetadata {
        put("simulated", true)
        extra.forEach { (key, value) -> put(key, value) }
    }

class SimulatedNetworkException : Exception("Simulated network failure")

class CartRejectedException(
    val reason: Reason,
) : Exception("Cart rejected: $reason") {
    enum class Reason(
        val userMessage: String,
    ) {
        OutOfStock("Out of stock"),
        Unknown("Could not add to cart"),
    }
}
