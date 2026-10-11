package dev.chayaphon.journeyscope.sample.shop.data

import dev.chayaphon.journeyscope.database.sqldelight.DatabaseTracing
import dev.chayaphon.journeyscope.sample.shop.db.CartDatabase

class CartLocalDataSource(
    private val database: CartDatabase,
    private val tracing: DatabaseTracing = DatabaseTracing(),
) {
    private val queries = database.cartItemQueries

    /** Returns the total item count in the cart after the write. */
    suspend fun upsert(
        productId: String,
        quantity: Int,
    ): Int =
        tracing.transaction<Int>(database, "cart.upsert") {
            val current = query("cart_items.quantityFor") { queries.quantityFor(productId).executeAsOneOrNull() ?: 0L }
            update("cart_items.upsert") { queries.upsert(productId, current + quantity) }
            query("cart_items.totalQuantity") { queries.totalQuantity().executeAsOne() }.toInt()
        }
}
