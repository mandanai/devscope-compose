package dev.chayaphon.journeyscope.sample.shop

import app.cash.sqldelight.db.SqlDriver
import dev.chayaphon.journeyscope.sample.shop.data.CartLocalDataSource
import dev.chayaphon.journeyscope.sample.shop.data.CartRepository
import dev.chayaphon.journeyscope.sample.shop.data.KtorCartApi
import dev.chayaphon.journeyscope.sample.shop.data.sampleHttpClient
import dev.chayaphon.journeyscope.sample.shop.db.CartDatabase
import dev.chayaphon.journeyscope.sample.shop.domain.AddToCartUseCase
import dev.chayaphon.journeyscope.sample.shop.ui.Product
import dev.chayaphon.journeyscope.sample.shop.ui.ProductViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Process-level wiring, kept outside composition so Activity recreation never cancels in-flight journeys. */
object SampleGraph {
    private const val IO_PARALLELISM = 2
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var driver: SqlDriver? = null

    /** Must be called by the platform entry point before the first journey. */
    fun configure(driver: SqlDriver) {
        this.driver = driver
    }

    val productViewModel: ProductViewModel by lazy {
        val database = CartDatabase(checkNotNull(driver) { "SampleGraph.configure(driver) was not called" })
        val repository =
            CartRepository(
                KtorCartApi(sampleHttpClient()),
                CartLocalDataSource(database),
                Dispatchers.Default.limitedParallelism(IO_PARALLELISM, "cart-io"),
            )
        ProductViewModel(
            product = Product(id = "sku-keyboard-01", name = "Mechanical Keyboard", price = "$129.00"),
            addToCartUseCase = AddToCartUseCase(repository),
            scope = scope,
        )
    }
}
