<!-- Generated from ai/scenarios/database-sqldelight.md by scripts/sync-skill-references.sh; edit there. -->
# SQLDelight database tracing

**Module:** `devscope-database-sqldelight` (Android + iOS). Package `dev.chayaphon.journeyscope.database.sqldelight`.

Only operations you wrap are recorded. There is no automatic capture of SQL; do not try to intercept `SqlDriver`.

## Edit

```kotlin
val tracing = DatabaseTracing()

suspend fun upsert(productId: String, quantity: Int): Int =
    withContext(ioDispatcher) {
        tracing.transaction<Int>(database, "cart.upsert") {
            val current = query("cart_items.quantityFor") { queries.quantityFor(productId).executeAsOneOrNull() ?: 0L }
            update("cart_items.upsert") { queries.upsert(productId, current + quantity) }
            query("cart_items.totalQuantity") { queries.totalQuantity().executeAsOne() }.toInt()
        }
    }
```

| API | Span |
|---|---|
| `query/insert/update/delete(label, rowCount?) { }` | one `DATABASE` span |
| `transaction(transacter, label) { }` | span for the transaction; inner calls are children; nested supported |
| `rollback(value)` inside transaction | span `OK`, outcome `rolled_back` |

## Rules

- Labels are stable names like `cart_items.upsert`, never SQL text, ids or user data.
- The SQL blocks the calling thread: run inside `withContext(ioDispatcher)`.
- Without an ambient journey the block just runs.
- Other databases (Room, etc.): no adapter; wrap with `traced(name, SpanCategory.DATABASE)` from [trace-usecase-flow](trace-usecase-flow.md).

## Verify

`DATABASE` spans with `db.operation`, `db.label`, and a shared `db.transaction.id` appear under the repository span.
