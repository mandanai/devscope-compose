package dev.chayaphon.journeyscope.sample.shop.domain

import dev.chayaphon.journeyscope.core.model.SpanCategory
import dev.chayaphon.journeyscope.sample.shop.SampleScenario
import dev.chayaphon.journeyscope.sample.shop.data.CartRepository
import dev.chayaphon.journeyscope.sample.shop.traced

class AddToCartUseCase(
    private val repository: CartRepository,
) {
    /** Returns the cart item count after the add. */
    suspend operator fun invoke(
        productId: String,
        quantity: Int,
        scenario: SampleScenario,
    ): Int = traced("AddToCartUseCase", SpanCategory.USE_CASE) { repository.add(productId, quantity, scenario) }
}
