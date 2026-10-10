package dev.journeyscope.sample.shop

import app.cash.sqldelight.db.SqlDriver
import dev.journeyscope.core.JourneyScope
import dev.journeyscope.core.JourneyScopeConfig
import dev.journeyscope.core.expectation.ExpectedSequence
import dev.journeyscope.core.expectation.ExpectedStep
import dev.journeyscope.core.model.SpanCategory
import dev.journeyscope.diagnostics.DiagnosticAnalyzer
import dev.journeyscope.export.JourneyExportController
import dev.journeyscope.export.persistence.JourneyPersistence
import dev.journeyscope.export.policy.ExportPolicy
import dev.journeyscope.export.share.ShareSink
import dev.journeyscope.export.storage.createTraceFileSystem
import dev.journeyscope.sample.shop.data.CartLocalDataSource
import dev.journeyscope.sample.shop.data.CartRepository
import dev.journeyscope.sample.shop.data.KtorCartApi
import dev.journeyscope.sample.shop.data.sampleHttpClient
import dev.journeyscope.sample.shop.db.CartDatabase
import dev.journeyscope.sample.shop.domain.AddToCartUseCase
import dev.journeyscope.sample.shop.ui.Product
import dev.journeyscope.sample.shop.ui.ProductViewModel
import dev.journeyscope.ui.export.exportFindings
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

/** Applies only to Add to Cart journeys; other journeys are never judged against it. */
val AddToCartCompletion =
    ExpectedSequence(
        name = "Add to Cart completion",
        steps =
            listOf(
                ExpectedStep.category("Network request", SpanCategory.NETWORK),
                ExpectedStep.category("Database write", SpanCategory.DATABASE, "cart.upsert"),
                ExpectedStep.category("UI render checkpoint", SpanCategory.UI_RENDER),
            ),
        appliesTo = { it.name == "Add to Cart" },
    )

/** Opt-in persistence and export for the sample; stays off until a platform entry point calls [install]. */
object SampleExport {
    private var traceDirectory: String? = null
    private var shareSink: ShareSink? = null

    var controller: JourneyExportController? = null
        private set

    fun install(
        traceDirectory: String,
        shareSink: ShareSink?,
    ) {
        this.traceDirectory = traceDirectory
        this.shareSink = shareSink
    }

    internal fun enable(config: JourneyScopeConfig) {
        val directory = traceDirectory
        if (directory == null) {
            JourneyScope.configure(config)
            return
        }
        val policy = ExportPolicy(persistenceEnabled = true, appVersion = SAMPLE_VERSION)
        val persistence = JourneyPersistence.install(config, policy, fileSystem = { createTraceFileSystem(directory) })
        JourneyScope.configure(config, persistence.recorder)
        controller =
            JourneyExportController(
                recorder = persistence.recorder,
                store = persistence.store,
                policy = policy,
                shareSink = shareSink,
                findings = DiagnosticAnalyzer().exportFindings(),
            )
    }

    private const val SAMPLE_VERSION = "sample"
}

fun enableJourneyScopeForSample() {
    if (!JourneyScope.isEnabled) SampleExport.enable(JourneyScopeConfig.debugOnly(isDebuggable = true))
}
