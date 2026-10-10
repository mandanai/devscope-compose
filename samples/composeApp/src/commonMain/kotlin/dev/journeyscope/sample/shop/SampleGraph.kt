package dev.journeyscope.sample.shop

import app.cash.sqldelight.db.SqlDriver
import dev.journeyscope.core.JourneyScope
import dev.journeyscope.core.JourneyScopeConfig
import dev.journeyscope.core.expectation.ExpectedSequence
import dev.journeyscope.core.expectation.ExpectedStep
import dev.journeyscope.core.model.SpanCategory
import dev.journeyscope.sample.shop.data.CartLocalDataSource
import dev.journeyscope.sample.shop.data.CartRepository
import dev.journeyscope.sample.shop.data.KtorCartApi
import dev.journeyscope.sample.shop.data.sampleHttpClient
import dev.journeyscope.sample.shop.db.CartDatabase
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
    private var driver: SqlDriver? = null

    /** Must be called by the platform entry point before the first journey. */
    fun configure(driver: SqlDriver) {
        this.driver = driver
    }

    val productViewModel: ProductViewModel by lazy {
        val database = CartDatabase(checkNotNull(driver) { "SampleGraph.configure(driver) was not called" })
        val repository = CartRepository(
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

/** Applies only to Add to Cart journeys; other journeys are never judged against it. */
val AddToCartCompletion = ExpectedSequence(
    name = "Add to Cart completion",
    steps = listOf(
        ExpectedStep.category("Network request", SpanCategory.NETWORK),
        ExpectedStep.category("Database write", SpanCategory.DATABASE, "cart.upsert"),
        ExpectedStep.category("UI render checkpoint", SpanCategory.UI_RENDER),
    ),
    appliesTo = { it.name == "Add to Cart" },
)

fun enableJourneyScopeForSample() {
    if (!JourneyScope.isEnabled) JourneyScope.configure(JourneyScopeConfig.debugOnly(isDebuggable = true))
}
