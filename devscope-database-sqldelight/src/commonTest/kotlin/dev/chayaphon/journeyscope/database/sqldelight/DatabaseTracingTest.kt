package dev.chayaphon.journeyscope.database.sqldelight

import dev.chayaphon.journeyscope.core.model.SpanCategory
import dev.chayaphon.journeyscope.core.model.SpanStatus
import dev.chayaphon.journeyscope.core.model.TraceStatus
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class DatabaseTracingTest {
    private val clock = FakeClock()
    private val tracer = testTracer(clock)
    private val tracing = counterTracing()
    private val database = FakeDatabase()

    @Test
    fun eachOperationKindRecordsADatabaseSpanWithItsDuration() =
        runTest {
            val journey = tracer.startJourney("j")
            withContext(journey.context()) {
                tracing.query("cart.select") {
                    clock.advance(10.milliseconds)
                    3
                }
                tracing.insert("cart.insert") { clock.advance(20.milliseconds) }
                tracing.update("cart.update") { clock.advance(30.milliseconds) }
                tracing.delete("cart.delete") { clock.advance(40.milliseconds) }
            }
            journey.finish()
            val trace = checkNotNull(journey.snapshot())

            assertEquals(listOf("query", "insert", "update", "delete"), trace.spans.map { it.text("db.operation") })
            assertTrue(trace.spans.all { it.category == SpanCategory.DATABASE && it.status == SpanStatus.OK })
            assertEquals(30, (trace.span("cart.update").endNanos!! - trace.span("cart.update").startNanos) / 1_000_000)
            assertEquals(
                1,
                trace.spans
                    .map { trace.id }
                    .distinct()
                    .size,
            )
        }

    @Test
    fun rowCountIsRecordedOnlyWhenSupplied() =
        runTest {
            val journey = tracer.startJourney("j")
            withContext(journey.context()) {
                tracing.query("with.rows", rowCount = { it.toLong() }) { 7 }
                tracing.query<Int>("without.rows") { 7 }
            }
            val trace = checkNotNull(journey.snapshot())
            assertEquals(7L, trace.span("with.rows").integer("db.rows"))
            assertNull(trace.span("without.rows").integer("db.rows"))
        }

    @Test
    fun spanIsParentedUnderTheAmbientSpan() =
        runTest {
            val journey = tracer.startJourney("j")
            withContext(journey.context()) {
                journey.traceSuspend("repo", SpanCategory.REPOSITORY) {
                    tracing.query("cart.select") { }
                }
            }
            val trace = checkNotNull(journey.snapshot())
            assertEquals(trace.span("repo").id, trace.span("cart.select").parentId)
        }

    @Test
    fun failedOperationEndsFailedAndRethrows() =
        runTest {
            val journey = tracer.startJourney("j")
            withContext(journey.context()) {
                assertFailsWith<IllegalStateException> { tracing.update("cart.update") { error("disk full") } }
            }
            val span = checkNotNull(journey.snapshot()).span("cart.update")
            assertEquals(SpanStatus.FAILED, span.status)
            assertTrue(
                span.failure
                    ?.type
                    .orEmpty()
                    .endsWith("IllegalStateException"),
            )
        }

    @Test
    fun withoutAJourneyTheBlockJustRuns() =
        runTest {
            assertEquals(5, tracing.query("q") { 5 })
            assertEquals(1, tracing.transaction<Int>(database, "tx") { 1 })
        }

    @Test
    fun committedTransactionHasChildrenSharingOneCorrelationId() =
        runTest {
            val journey = tracer.startJourney("j")
            withContext(journey.context()) {
                tracing.transaction<Unit>(database, "cart.upsert") {
                    update("cart.write") { clock.advance(5.milliseconds) }
                    query("cart.read") { clock.advance(5.milliseconds) }
                }
            }
            journey.finish()
            val trace = checkNotNull(journey.snapshot())
            val tx = trace.span("cart.upsert")

            assertEquals("committed", tx.text("db.transaction.outcome"))
            assertEquals("tx-1", tx.text("db.transaction.id"))
            assertEquals(tx.id, trace.span("cart.write").parentId)
            assertEquals(tx.id, trace.span("cart.read").parentId)
            assertEquals("tx-1", trace.span("cart.write").text("db.transaction.id"))
            assertEquals(TraceStatus.COMPLETED, trace.status)
        }

    @Test
    fun explicitRollbackIsRecordedAsRolledBackAndReturnsTheValue() =
        runTest {
            val journey = tracer.startJourney("j")
            val result =
                withContext(journey.context()) {
                    tracing.transaction(database, "cart.upsert") {
                        update("cart.write") { }
                        rollback(42)
                    }
                }
            val tx = checkNotNull(journey.snapshot()).span("cart.upsert")
            assertEquals(42, result)
            assertEquals("rolled_back", tx.text("db.transaction.outcome"))
            assertEquals(SpanStatus.OK, tx.status)
        }

    @Test
    fun exceptionInsideTransactionFailsTheSpanAndRethrows() =
        runTest {
            val journey = tracer.startJourney("j")
            withContext(journey.context()) {
                assertFailsWith<IllegalArgumentException> {
                    tracing.transaction<Unit>(database, "cart.upsert") {
                        update("cart.write") { throw IllegalArgumentException("constraint") }
                    }
                }
            }
            val trace = checkNotNull(journey.snapshot())
            assertEquals(SpanStatus.FAILED, trace.span("cart.write").status)
            assertEquals(SpanStatus.FAILED, trace.span("cart.upsert").status)
            assertEquals("failed", trace.span("cart.upsert").text("db.transaction.outcome"))
        }

    @Test
    fun nestedTransactionKeepsTheOuterCorrelationId() =
        runTest {
            val journey = tracer.startJourney("j")
            withContext(journey.context()) {
                tracing.transaction<Unit>(database, "outer") {
                    transaction<Unit>("inner") { update("write") { } }
                }
            }
            val trace = checkNotNull(journey.snapshot())
            assertEquals(trace.span("outer").id, trace.span("inner").parentId)
            assertEquals(trace.span("inner").id, trace.span("write").parentId)
            assertEquals("tx-1", trace.span("inner").text("db.transaction.id"))
        }

    @Test
    fun rollbackInsideANestedTransactionClosesTheInnerSpanCleanly() =
        runTest {
            val journey = tracer.startJourney("j")
            withContext(journey.context()) {
                tracing.transaction<Unit>(database, "outer") {
                    transaction<Unit>("inner") { rollback(Unit) }
                }
            }
            journey.finish()
            val trace = checkNotNull(journey.snapshot())
            val inner = trace.span("inner")

            assertEquals(SpanStatus.OK, inner.status)
            assertEquals(false, inner.endedBySystem)
            assertEquals("rolled_back", inner.text("db.transaction.outcome"))
            assertEquals("rolled_back", trace.span("outer").text("db.transaction.outcome"))
            assertTrue(trace.spans.none { it.endedBySystem })
        }

    @Test
    fun metadataNeverContainsSqlOrArguments() =
        runTest {
            val journey = tracer.startJourney("j")
            withContext(journey.context()) {
                tracing.query("users.byEmail") { "secret@example.com" }
            }
            val span = checkNotNull(journey.snapshot()).span("users.byEmail")
            assertEquals(setOf("db.operation", "db.label"), span.metadata.entries.keys)
        }
}
