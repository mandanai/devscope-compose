# Database tracing (SQLDelight)

`devscope-database-sqldelight` — `dev.journeyscope.database.sqldelight`. Android + iOS (Kotlin Multiplatform); depends on `devscope-core` and the SQLDelight runtime only.

## What is captured
Only operations you wrap with `DatabaseTracing` are recorded. There is **no** automatic capture of every SQL statement: SQLDelight's `SqlDriver` is deliberately not intercepted, because its calls are not suspending and cannot see the coroutine-context parent, so the resulting spans could not be placed under the right operation.

```kotlin
val tracing = DatabaseTracing()

// Inside withContext(ioDispatcher): the SQL itself blocks the calling thread.
suspend fun upsert(productId: String, quantity: Int): Int =
    tracing.transaction<Int>(database, "cart.upsert") {
        val current = query("cart_items.quantityFor") { queries.quantityFor(productId).executeAsOneOrNull() ?: 0L }
        update("cart_items.upsert") { queries.upsert(productId, current + quantity) }
        query("cart_items.totalQuantity") { queries.totalQuantity().executeAsOne() }.toInt()
    }
```

| API | Span |
| --- | --- |
| `query` / `insert` / `update` / `delete(label) { }` | one `DATABASE` span, `OK` or `FAILED`, parented to the ambient span |
| `transaction(transacter, label) { }` | a `DATABASE` span for the transaction; operations inside are its children; nested `transaction` supported |
| `rollback(value)` inside a transaction | span stays `OK`, outcome `rolled_back` |
| exception | span `FAILED`, outcome `failed`, exception rethrown (SQLDelight rolls back) |
| cancellation | span `CANCELLED` |

Without an ambient journey the block simply runs. Duration is the span's measured duration.

## Metadata (safe by construction)
`db.operation` (`query|insert|update|delete|transaction`), `db.label` (the label you pass), `db.transaction.id` (shared by a transaction and its children), `db.transaction.outcome` (`committed|rolled_back|failed`), `db.rows` (only when you pass a `rowCount` lambda). SQL text, bind arguments, credentials and records are never read. Labels should be stable names, not interpolated data; they pass through the core sanitizer like every span name.

## Correlation
The span uses the journey carried by the coroutine context, so it shares the `JourneyTrace` id with the UI, view-model, use-case, network and state spans. In the sample the DB spans appear under `CartRepository.add`, after `validateCartResponse` and before the state emission.
