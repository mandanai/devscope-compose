package dev.chayaphon.journeyscope.sample.shop.data

import dev.chayaphon.journeyscope.core.model.SpanCategory
import dev.chayaphon.journeyscope.core.model.diagnosticMetadata
import dev.chayaphon.journeyscope.sample.shop.CartRejectedException
import dev.chayaphon.journeyscope.sample.shop.SampleScenario
import dev.chayaphon.journeyscope.sample.shop.traced
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class CartRepository(
    private val remote: CartRemoteDataSource,
    private val local: CartLocalDataSource,
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun add(
        productId: String,
        quantity: Int,
        scenario: SampleScenario,
    ): Int =
        traced("CartRepository.add", SpanCategory.REPOSITORY) {
            withContext(ioDispatcher) {
                val response = remote.addItem(productId, quantity, scenario)
                validate(response)
                local.upsert(productId, response.quantity)
            }
        }

    private suspend fun validate(response: CartApiResponse) =
        traced("validateCartResponse", SpanCategory.BUSINESS_VALIDATION, diagnosticMetadata { put("rule", "item_accepted") }) {
            if (!response.accepted) {
                val reason =
                    if (response.rejectionReason == "OUT_OF_STOCK") {
                        CartRejectedException.Reason.OutOfStock
                    } else {
                        CartRejectedException.Reason.Unknown
                    }
                throw CartRejectedException(reason)
            }
        }
}
