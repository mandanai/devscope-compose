package dev.journeyscope.sample.shop

import dev.journeyscope.core.JourneyScope
import dev.journeyscope.core.JourneyScopeConfig
import dev.journeyscope.sample.shop.data.CartLocalDataSource
import dev.journeyscope.sample.shop.data.CartRepository
import dev.journeyscope.sample.shop.data.SimulatedCartApi
import dev.journeyscope.sample.shop.domain.AddToCartUseCase
import dev.journeyscope.sample.shop.ui.Product
import dev.journeyscope.sample.shop.ui.ProductViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Process-level wiring, kept outside composition so Activity recreation never cancels in-flight journeys. */
object SampleGraph {
    private const val IO_PARALLELISM = 2
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val productViewModel: ProductViewModel by lazy {
        val repository = CartRepository(SimulatedCartApi(), CartLocalDataSource(), Dispatchers.Default.limitedParallelism(IO_PARALLELISM, "cart-io"))
        ProductViewModel(
            product = Product(id = "sku-keyboard-01", name = "Mechanical Keyboard", price = "$129.00"),
            addToCartUseCase = AddToCartUseCase(repository),
            scope = scope,
        )
    }
}

fun enableJourneyScopeForSample() {
    if (!JourneyScope.isEnabled) JourneyScope.configure(JourneyScopeConfig.debugOnly(isDebuggable = true))
}
