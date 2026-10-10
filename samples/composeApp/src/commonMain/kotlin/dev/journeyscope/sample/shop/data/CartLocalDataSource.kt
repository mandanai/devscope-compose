package dev.journeyscope.sample.shop.data

import dev.journeyscope.core.model.SpanCategory
import dev.journeyscope.sample.shop.LatencyProfile
import dev.journeyscope.sample.shop.simulatedMetadata
import dev.journeyscope.sample.shop.traced
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** In-memory stand-in for a database table. */
class CartLocalDataSource(private val latency: LatencyProfile = LatencyProfile.Default) {
    private val mutex = Mutex()
    private val quantities = mutableMapOf<String, Int>()

    /** Returns the total item count in the cart after the write. */
    suspend fun upsert(productId: String, quantity: Int): Int =
        traced("cart.upsert", SpanCategory.DATABASE, simulatedMetadata("table" to "cart_items")) {
            delay(latency.database)
            mutex.withLock {
                quantities[productId] = (quantities[productId] ?: 0) + quantity
                quantities.values.sum()
            }
        }
}
