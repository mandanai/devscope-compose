package dev.journeyscope.database.sqldelight

import app.cash.sqldelight.TransactionWithReturn
import app.cash.sqldelight.Transacter
import dev.journeyscope.core.Journey
import dev.journeyscope.core.SpanHandle
import dev.journeyscope.core.currentTraceContext
import dev.journeyscope.core.model.DiagnosticMetadata
import dev.journeyscope.core.model.SpanCategory
import dev.journeyscope.core.model.SpanStatus
import dev.journeyscope.core.model.TraceMetadataKeys
import dev.journeyscope.core.model.diagnosticMetadata
import kotlinx.coroutines.CancellationException
import kotlin.random.Random

public enum class DatabaseOperation(internal val wireName: String) {
    Query("query"),
    Insert("insert"),
    Update("update"),
    Delete("delete"),
    Transaction("transaction"),
}

public class DatabaseTraceConfig(
    public val captureRowCounts: Boolean = true,
    public val transactionIdGenerator: () -> String = { Random.nextLong().toULong().toString(16) },
)

/**
 * Explicit tracing at data-access boundaries. Only operations wrapped through this class are
 * recorded; SQLDelight's driver is deliberately not intercepted. Without an ambient journey the
 * block simply runs. Blocking SQL runs on the caller's dispatcher, so call these inside
 * `withContext(ioDispatcher)`.
 */
public class DatabaseTracing(private val config: DatabaseTraceConfig = DatabaseTraceConfig()) {
    public suspend fun <T> query(label: String, rowCount: ((T) -> Long)? = null, block: () -> T): T =
        traced(DatabaseOperation.Query, label, rowCount, block)

    public suspend fun <T> insert(label: String, rowCount: ((T) -> Long)? = null, block: () -> T): T =
        traced(DatabaseOperation.Insert, label, rowCount, block)

    public suspend fun <T> update(label: String, rowCount: ((T) -> Long)? = null, block: () -> T): T =
        traced(DatabaseOperation.Update, label, rowCount, block)

    public suspend fun <T> delete(label: String, rowCount: ((T) -> Long)? = null, block: () -> T): T =
        traced(DatabaseOperation.Delete, label, rowCount, block)

    public suspend fun <R> transaction(
        transacter: Transacter,
        label: String,
        noEnclosing: Boolean = false,
        body: TracedTransactionScope<R>.() -> R,
    ): R {
        val context = currentTraceContext()
        val transactionId = config.transactionIdGenerator()
        val journey = context?.journey
        val span = context?.beginChildSpan(label, SpanCategory.DATABASE, metadata(DatabaseOperation.Transaction, label, transactionId))
        var rolledBack = false
        try {
            val result = transacter.transactionWithResult(noEnclosing) {
                afterRollback { rolledBack = true }
                TracedTransactionScope(this, journey, span, transactionId, config).body()
            }
            span?.end(metadata = outcome(if (rolledBack) ROLLED_BACK else COMMITTED))
            return result
        } catch (cancelled: CancellationException) {
            span?.end(SpanStatus.CANCELLED, outcome(ROLLED_BACK))
            throw cancelled
        } catch (failure: Throwable) {
            span?.fail(failure, outcome(FAILED))
            throw failure
        }
    }

    private suspend fun <T> traced(
        operation: DatabaseOperation,
        label: String,
        rowCount: ((T) -> Long)?,
        block: () -> T,
    ): T {
        val context = currentTraceContext() ?: return block()
        val span = context.beginChildSpan(label, SpanCategory.DATABASE, metadata(operation, label, null))
        return runSpan(span, rowCount, config, block)
    }
}

/** Receiver of a traced transaction body; operations here are children of the transaction span. */
public class TracedTransactionScope<R> internal constructor(
    private val transaction: TransactionWithReturn<R>,
    private val journey: Journey?,
    private val parent: SpanHandle?,
    public val transactionId: String,
    private val config: DatabaseTraceConfig,
) {
    public fun <T> query(label: String, rowCount: ((T) -> Long)? = null, block: () -> T): T =
        traced(DatabaseOperation.Query, label, rowCount, block)

    public fun <T> insert(label: String, rowCount: ((T) -> Long)? = null, block: () -> T): T =
        traced(DatabaseOperation.Insert, label, rowCount, block)

    public fun <T> update(label: String, rowCount: ((T) -> Long)? = null, block: () -> T): T =
        traced(DatabaseOperation.Update, label, rowCount, block)

    public fun <T> delete(label: String, rowCount: ((T) -> Long)? = null, block: () -> T): T =
        traced(DatabaseOperation.Delete, label, rowCount, block)

    public fun rollback(returnValue: R): Nothing = transaction.rollback(returnValue)

    public fun <N> transaction(label: String, body: TracedTransactionScope<N>.() -> N): N {
        val span = journey?.beginSpan(label, SpanCategory.DATABASE, metadata(DatabaseOperation.Transaction, label, transactionId), parent)
        val result = try {
            transaction.transaction<N> { TracedTransactionScope(this, journey, span, transactionId, config).body() }
        } catch (cancelled: CancellationException) {
            span?.end(SpanStatus.CANCELLED, outcome(ROLLED_BACK))
            throw cancelled
        } catch (failure: Exception) {
            span?.fail(failure, outcome(FAILED))
            throw failure
        } catch (rollbackSignal: Throwable) {
            // SQLDelight unwinds an explicit rollback() through enclosing transactions as a bare Throwable.
            span?.end(metadata = outcome(ROLLED_BACK))
            throw rollbackSignal
        }
        span?.end()
        return result
    }

    private fun <T> traced(operation: DatabaseOperation, label: String, rowCount: ((T) -> Long)?, block: () -> T): T {
        val span = journey?.beginSpan(label, SpanCategory.DATABASE, metadata(operation, label, transactionId), parent)
        return runSpan(span, rowCount, config, block)
    }
}

private const val COMMITTED = "committed"
private const val ROLLED_BACK = "rolled_back"
private const val FAILED = "failed"

private fun metadata(operation: DatabaseOperation, label: String, transactionId: String?): DiagnosticMetadata =
    diagnosticMetadata {
        put(TraceMetadataKeys.DB_OPERATION, operation.wireName)
        put(TraceMetadataKeys.DB_LABEL, label)
        transactionId?.let { put(TraceMetadataKeys.DB_TRANSACTION_ID, it) }
    }

private fun outcome(value: String): DiagnosticMetadata =
    diagnosticMetadata { put(TraceMetadataKeys.DB_TRANSACTION_OUTCOME, value) }

private inline fun <T> runSpan(span: SpanHandle?, noinline rowCount: ((T) -> Long)?, config: DatabaseTraceConfig, block: () -> T): T {
    if (span == null) return block()
    val result = try {
        block()
    } catch (cancelled: CancellationException) {
        span.end(SpanStatus.CANCELLED)
        throw cancelled
    } catch (failure: Throwable) {
        span.fail(failure)
        throw failure
    }
    val rows = if (config.captureRowCounts) rowCount?.let { runCatching { it(result) }.getOrNull() } else null
    span.end(metadata = if (rows != null) diagnosticMetadata { put(TraceMetadataKeys.DB_ROWS, rows) } else DiagnosticMetadata.Empty)
    return result
}
