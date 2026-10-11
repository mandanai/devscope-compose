package dev.chayaphon.journeyscope.database.sqldelight

import app.cash.sqldelight.Transacter
import app.cash.sqldelight.TransactionWithReturn
import kotlin.random.Random

public enum class DatabaseOperation {
    Query,
    Insert,
    Update,
    Delete,
    Transaction,
}

public class DatabaseTraceConfig(
    public val captureRowCounts: Boolean = true,
    public val transactionIdGenerator: () -> String = { Random.nextLong().toULong().toString(16) },
)

/** Release-build twin: every block still runs (transactions included), nothing is recorded. */
public class DatabaseTracing(
    private val config: DatabaseTraceConfig = DatabaseTraceConfig(),
) {
    public suspend fun <T> query(
        label: String,
        rowCount: ((T) -> Long)? = null,
        block: () -> T,
    ): T = block()

    public suspend fun <T> insert(
        label: String,
        rowCount: ((T) -> Long)? = null,
        block: () -> T,
    ): T = block()

    public suspend fun <T> update(
        label: String,
        rowCount: ((T) -> Long)? = null,
        block: () -> T,
    ): T = block()

    public suspend fun <T> delete(
        label: String,
        rowCount: ((T) -> Long)? = null,
        block: () -> T,
    ): T = block()

    public suspend fun <R> transaction(
        transacter: Transacter,
        label: String,
        noEnclosing: Boolean = false,
        body: TracedTransactionScope<R>.() -> R,
    ): R {
        val transactionId = config.transactionIdGenerator()
        return transacter.transactionWithResult(noEnclosing) { TracedTransactionScope(this, transactionId).body() }
    }
}

public class TracedTransactionScope<R> internal constructor(
    private val transaction: TransactionWithReturn<R>,
    public val transactionId: String,
) {
    public fun <T> query(
        label: String,
        rowCount: ((T) -> Long)? = null,
        block: () -> T,
    ): T = block()

    public fun <T> insert(
        label: String,
        rowCount: ((T) -> Long)? = null,
        block: () -> T,
    ): T = block()

    public fun <T> update(
        label: String,
        rowCount: ((T) -> Long)? = null,
        block: () -> T,
    ): T = block()

    public fun <T> delete(
        label: String,
        rowCount: ((T) -> Long)? = null,
        block: () -> T,
    ): T = block()

    public fun rollback(returnValue: R): Nothing = transaction.rollback(returnValue)

    public fun <N> transaction(
        label: String,
        body: TracedTransactionScope<N>.() -> N,
    ): N = transaction.transaction<N> { TracedTransactionScope(this, transactionId).body() }
}
