package dev.journeyscope.sample.shop.ui

enum class CartOutcome { None, Added, Failed, Cancelled }

enum class CartPhase { Idle, Adding, Added, Failed, Cancelled }

data class CartUiState(
    val cartCount: Int = 0,
    val pending: Int = 0,
    val outcome: CartOutcome = CartOutcome.None,
    val message: String? = null,
    val renderToken: Long = 0,
) {
    val phase: CartPhase
        get() =
            when {
                pending > 0 -> CartPhase.Adding
                outcome == CartOutcome.Added -> CartPhase.Added
                outcome == CartOutcome.Failed -> CartPhase.Failed
                outcome == CartOutcome.Cancelled -> CartPhase.Cancelled
                else -> CartPhase.Idle
            }

    fun adding(): CartUiState = copy(pending = pending + 1)

    // Concurrent adds can finish out of order, and the cart only grows.
    fun added(count: Int): CartUiState = copy(pending = pending - 1, cartCount = maxOf(cartCount, count), outcome = CartOutcome.Added, message = null)

    fun failed(message: String): CartUiState = copy(pending = pending - 1, outcome = CartOutcome.Failed, message = message)

    fun cancelled(): CartUiState = copy(pending = pending - 1, outcome = CartOutcome.Cancelled, message = "Add to cart cancelled")
}
