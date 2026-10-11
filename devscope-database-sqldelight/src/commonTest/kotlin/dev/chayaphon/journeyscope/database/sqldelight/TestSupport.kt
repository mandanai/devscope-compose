package dev.chayaphon.journeyscope.database.sqldelight

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import dev.chayaphon.journeyscope.core.IdGenerator
import dev.chayaphon.journeyscope.core.JourneyScopeConfig
import dev.chayaphon.journeyscope.core.JourneyTracer
import dev.chayaphon.journeyscope.core.model.JourneySpan
import dev.chayaphon.journeyscope.core.model.JourneyTrace
import dev.chayaphon.journeyscope.core.model.MetadataValue
import dev.chayaphon.journeyscope.core.timing.JourneyClock
import kotlin.time.Duration

class FakeClock : JourneyClock {
    private var nanos = 0L

    fun advance(by: Duration) {
        nanos += by.inWholeNanoseconds
    }

    override fun monotonicNanos(): Long = nanos

    override fun wallClockMillis(): Long = 1_700_000_000_000 + nanos / 1_000_000
}

class SequentialIds : IdGenerator {
    private var next = 0

    override fun nextId(): String = (++next).toString()
}

fun testTracer(clock: FakeClock) = JourneyTracer(JourneyScopeConfig(enabled = true), clock = clock, idGenerator = SequentialIds())

fun counterTracing(): DatabaseTracing {
    var next = 0
    return DatabaseTracing(DatabaseTraceConfig(transactionIdGenerator = { "tx-${++next}" }))
}

class FakeDatabase(
    driver: SqlDriver = FakeSqlDriver(),
) : TransacterImpl(driver)

class FakeSqlDriver : SqlDriver {
    private var current: Transacter.Transaction? = null

    override fun newTransaction(): QueryResult<Transacter.Transaction> {
        val transaction =
            object : Transacter.Transaction() {
                override val enclosingTransaction: Transacter.Transaction? = current

                override fun endTransaction(successful: Boolean): QueryResult<Unit> {
                    current = enclosingTransaction
                    return QueryResult.Unit
                }
            }
        current = transaction
        return QueryResult.Value(transaction)
    }

    override fun currentTransaction(): Transacter.Transaction? = current

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> = error("unused")

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> = error("unused")

    override fun addListener(
        vararg queryKeys: String,
        listener: Query.Listener,
    ) = Unit

    override fun removeListener(
        vararg queryKeys: String,
        listener: Query.Listener,
    ) = Unit

    override fun notifyListeners(vararg queryKeys: String) = Unit

    override fun close() = Unit
}

fun JourneyTrace.span(name: String): JourneySpan = spans.single { it.name == name }

fun JourneySpan.text(key: String): String? = (metadata[key] as? MetadataValue.Text)?.value

fun JourneySpan.integer(key: String): Long? = (metadata[key] as? MetadataValue.Integer)?.value
