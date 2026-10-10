package dev.journeyscope.ui.demo

import dev.journeyscope.core.model.DiagnosticMetadata
import dev.journeyscope.core.model.EventId
import dev.journeyscope.core.model.FailureInfo
import dev.journeyscope.core.model.JourneyEvent
import dev.journeyscope.core.model.JourneySpan
import dev.journeyscope.core.model.JourneySummary
import dev.journeyscope.core.model.JourneyTrace
import dev.journeyscope.core.model.SpanCategory
import dev.journeyscope.core.model.SpanId
import dev.journeyscope.core.model.SpanStatus
import dev.journeyscope.core.model.TraceId
import dev.journeyscope.core.model.TraceStatus
import dev.journeyscope.core.model.diagnosticMetadata
import dev.journeyscope.ui.mapping.MetadataDisplayPolicy
import dev.journeyscope.ui.state.JourneyTraceSource
import dev.journeyscope.ui.state.staticTraceSource
import kotlin.time.Duration.Companion.milliseconds

private const val NANOS_PER_MILLI = 1_000_000L
private const val BASE_NANOS = 1_000_000_000_000L
private const val BASE_WALL_MILLIS = 1_700_000_000_000L
private const val LARGE_GROUPS = 250

/** Deterministic fixtures; the 8-stage "Add to Cart" sample models sequential stages summing to 1,265 ms. */
public object DemoJourneys {
    internal const val NOW_NANOS: Long = BASE_NANOS + 100_000 * NANOS_PER_MILLI

    public val source: JourneyTraceSource by lazy { staticTraceSource(all, NOW_NANOS) }

    internal val all: List<JourneyTrace> by lazy {
        listOf(addToCart(), openProduct(), checkoutFailed(), syncOrdersRunning(), syncProfileRunning(), coldStartEmpty(), importCatalogLarge())
    }

    internal fun addToCart(): JourneyTrace {
        val stages = listOf(
            Stage("Button Click", SpanCategory.UI_INTERACTION, 0, "ProductDetailScreen · Add to Cart"),
            Stage("ViewModel Action", SpanCategory.VIEW_MODEL, 12, "ProductViewModel.addToCart()"),
            Stage("UseCase Execute", SpanCategory.USE_CASE, 34, "AddToCartUseCase"),
            Stage("API Request", SpanCategory.NETWORK, 940, "POST /cart/items · HTTP 200"),
            Stage("Business Validation", SpanCategory.BUSINESS_VALIDATION, 4, "returnCode = 0000"),
            Stage("Database Update", SpanCategory.DATABASE, 150, "CartEntity insert/update"),
            Stage("UI State Emission", SpanCategory.STATE_EMISSION, 15, "CartUiState.Success"),
            Stage("UI Render Checkpoint", SpanCategory.UI_RENDER, 110, "Cart count updated"),
        )
        return sequentialTrace("CART-1024", "Add to Cart", 0, stages, TraceStatus.COMPLETED, demoSource = true)
    }

    internal fun slowJourney(): JourneyTrace = addToCart()

    internal fun openProduct(): JourneyTrace = sequentialTrace(
        id = "PROD-2201",
        name = "Open Product",
        startMillis = 2_000,
        stages = listOf(
            Stage("Product Tap", SpanCategory.UI_INTERACTION, 0, "ProductListScreen · item tap"),
            Stage("Load Product", SpanCategory.NETWORK, 180, "GET /products/42 · HTTP 200"),
            Stage("Render Product", SpanCategory.UI_RENDER, 70, "ProductDetailScreen"),
        ),
        status = TraceStatus.COMPLETED,
    )

    internal fun checkoutFailed(): JourneyTrace {
        val trace = sequentialTrace(
            id = "ORD-7781",
            name = "Checkout",
            startMillis = 4_000,
            stages = listOf(
                Stage("Button Click", SpanCategory.UI_INTERACTION, 0, "CartScreen · Checkout"),
                Stage("Create Order", SpanCategory.NETWORK, 1_800, "POST /orders · HTTP 503", SpanStatus.FAILED, FailureInfo("IOException", "Service unavailable")),
            ),
            status = TraceStatus.FAILED,
        )
        return trace.copy(failure = FailureInfo("IOException", "Service unavailable"))
    }

    internal fun syncOrdersRunning(): JourneyTrace {
        val start = BASE_NANOS + 99_100 * NANOS_PER_MILLI
        val spans = listOf(
            span("sync-1", "Read Local Orders", SpanCategory.DATABASE, start, 120, 0, "OrderDao.pending()"),
            span("sync-2", "Upload Orders", SpanCategory.NETWORK, start + 120 * NANOS_PER_MILLI, null, 1, "POST /orders/sync", SpanStatus.RUNNING),
        )
        return JourneyTrace(
            id = TraceId("SYNC-3310"), name = "Sync Orders", status = TraceStatus.RUNNING,
            startNanos = start, startWallMillis = BASE_WALL_MILLIS + 99_100, endNanos = null, endWallMillis = null,
            spans = spans, events = emptyList(), failure = null, metadata = demoMetadata(), droppedItems = 0, summary = null,
        )
    }

    internal fun syncProfileRunning(): JourneyTrace {
        val start = BASE_NANOS + 99_600 * NANOS_PER_MILLI
        return JourneyTrace(
            id = TraceId("SYNC-3311"), name = "Sync Profile", status = TraceStatus.RUNNING,
            startNanos = start, startWallMillis = BASE_WALL_MILLIS + 99_600, endNanos = null, endWallMillis = null,
            spans = listOf(span("prof-1", "Fetch Profile", SpanCategory.NETWORK, start, null, 0, "GET /me", SpanStatus.RUNNING)),
            events = listOf(event("prof-e1", "Refresh Requested", SpanCategory.UI_INTERACTION, start, 0)),
            failure = null, metadata = demoMetadata(), droppedItems = 0, summary = null,
        )
    }

    internal fun coldStartEmpty(): JourneyTrace {
        val start = BASE_NANOS + 8_000 * NANOS_PER_MILLI
        return JourneyTrace(
            id = TraceId("START-1"), name = "Cold Start", status = TraceStatus.COMPLETED,
            startNanos = start, startWallMillis = BASE_WALL_MILLIS + 8_000, endNanos = start + 50 * NANOS_PER_MILLI,
            endWallMillis = BASE_WALL_MILLIS + 8_050, spans = emptyList(), events = emptyList(),
            failure = null, metadata = demoMetadata(), droppedItems = 0, summary = null,
        )
    }

    internal fun importCatalogLarge(groups: Int = LARGE_GROUPS): JourneyTrace {
        val start = BASE_NANOS + 10_000 * NANOS_PER_MILLI
        val spans = ArrayList<JourneySpan>(groups * 4)
        var offset = 0L
        for (g in 0 until groups) {
            val groupStart = start + offset * NANOS_PER_MILLI
            val parentId = "imp-$g"
            spans += span(parentId, "Import Batch $g", SpanCategory.USE_CASE, groupStart, 90, g * 4L, "batch $g")
            spans += span("$parentId-net", "Download Page $g", SpanCategory.NETWORK, groupStart, 50, g * 4L + 1, "GET /catalog?page=$g", parent = parentId)
            spans += span("$parentId-db", "Persist Page $g", SpanCategory.DATABASE, groupStart + 30 * NANOS_PER_MILLI, 40, g * 4L + 2, "CatalogDao.upsert", parent = parentId)
            spans += span("$parentId-ui", "Render Progress $g", SpanCategory.UI_RENDER, groupStart + 70 * NANOS_PER_MILLI, 20, g * 4L + 3, "ImportProgress", parent = parentId)
            offset += 90
        }
        return JourneyTrace(
            id = TraceId("IMP-9000"), name = "Import Catalog", status = TraceStatus.COMPLETED,
            startNanos = start, startWallMillis = BASE_WALL_MILLIS + 10_000, endNanos = start + offset * NANOS_PER_MILLI,
            endWallMillis = BASE_WALL_MILLIS + 10_000 + offset, spans = spans, events = emptyList(),
            failure = null, metadata = demoMetadata(), droppedItems = 0, summary = null,
        )
    }

    private class Stage(
        val name: String,
        val category: SpanCategory,
        val durationMillis: Long,
        val detail: String,
        val status: SpanStatus = SpanStatus.OK,
        val failure: FailureInfo? = null,
    )

    private fun sequentialTrace(
        id: String,
        name: String,
        startMillis: Long,
        stages: List<Stage>,
        status: TraceStatus,
        demoSource: Boolean = true,
    ): JourneyTrace {
        val start = BASE_NANOS + startMillis * NANOS_PER_MILLI
        var cursor = start
        val spans = stages.mapIndexed { index, stage ->
            span("$id-$index", stage.name, stage.category, cursor, stage.durationMillis, index.toLong(), stage.detail, stage.status, stage.failure)
                .also { cursor += stage.durationMillis * NANOS_PER_MILLI }
        }
        val trace = JourneyTrace(
            id = TraceId(id), name = name, status = status,
            startNanos = start, startWallMillis = BASE_WALL_MILLIS + startMillis, endNanos = cursor,
            endWallMillis = BASE_WALL_MILLIS + startMillis + (cursor - start) / NANOS_PER_MILLI,
            spans = spans, events = emptyList(), failure = null,
            metadata = if (demoSource) demoMetadata() else DiagnosticMetadata.Empty, droppedItems = 0, summary = null,
        )
        val timing = trace.timing()
        return trace.copy(
            summary = JourneySummary(
                name = name, status = status, duration = timing.duration, spanCount = spans.size, eventCount = 0,
                failedSpanCount = spans.count { it.status == SpanStatus.FAILED }, droppedItems = 0,
                isSlow = timing.duration >= 1_000.milliseconds, criticalPathDuration = timing.criticalPathDuration,
                failureType = null,
            ),
        )
    }

    private fun span(
        id: String,
        name: String,
        category: SpanCategory,
        startNanos: Long,
        durationMillis: Long?,
        sequence: Long,
        detail: String,
        status: SpanStatus = SpanStatus.OK,
        failure: FailureInfo? = null,
        parent: String? = null,
    ): JourneySpan = JourneySpan(
        id = SpanId(id), parentId = parent?.let(::SpanId), name = name, category = category, sequence = sequence,
        startNanos = startNanos, startWallMillis = BASE_WALL_MILLIS + (startNanos - BASE_NANOS) / NANOS_PER_MILLI,
        endNanos = durationMillis?.let { startNanos + it * NANOS_PER_MILLI }, status = status, failure = failure,
        endedBySystem = false, metadata = diagnosticMetadata { put(MetadataDisplayPolicy.DETAIL_KEY, detail); put("thread", "main") },
    )

    private fun event(id: String, name: String, category: SpanCategory, atNanos: Long, sequence: Long): JourneyEvent = JourneyEvent(
        id = EventId(id), name = name, category = category, sequence = sequence, atNanos = atNanos,
        atWallMillis = BASE_WALL_MILLIS + (atNanos - BASE_NANOS) / NANOS_PER_MILLI, spanId = null, metadata = DiagnosticMetadata.Empty,
    )

    private fun demoMetadata(): DiagnosticMetadata = diagnosticMetadata { put(MetadataDisplayPolicy.SOURCE_KEY, "Demo data") }
}
