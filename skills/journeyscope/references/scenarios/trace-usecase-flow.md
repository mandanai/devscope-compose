<!-- Generated from ai/scenarios/trace-usecase-flow.md by scripts/sync-skill-references.sh; edit there. -->
# Trace a UI → ViewModel → UseCase → Repository flow

**When:** always, for the flow the user named.
**Reference:** `samples/composeApp/.../shop/` (`ProductViewModel`, `AddToCartUseCase`, `CartRepository`, `SampleTracing`).

## Edits

1. **Host helper** — one place, in the module that owns shared app code. It joins the ambient journey and is a no-op when none exists, so layers need no `Journey` parameter.

```kotlin
suspend fun <T> traced(
    name: String,
    category: SpanCategory,
    metadata: DiagnosticMetadata = DiagnosticMetadata.Empty,
    block: suspend () -> T,
): T {
    val journey = currentTraceContext()?.journey ?: return block()
    return journey.traceSuspend(name, category, metadata, block)
}
```

Imports: `dev.chayaphon.journeyscope.core.currentTraceContext`, `core.model.SpanCategory`, `core.model.DiagnosticMetadata`.

2. **Start the journey at the user action** (ViewModel intent handler or click handler). Prefer `withJourney`: it finishes, fails or cancels from the block's outcome and rethrows.

```kotlin
fun addToCart() {
    viewModelScope.launch {
        JourneyScope.withJourney("Add to Cart", diagnosticMetadata { put("productId", product.id) }) {
            recordEvent("Add tapped", SpanCategory.UI_INTERACTION)
            addToCartUseCase(product.id, quantity)
        }
    }
}
```

3. **Wrap each layer** with `traced`:

```kotlin
suspend operator fun invoke(id: String, qty: Int) =
    traced("AddToCartUseCase", SpanCategory.USE_CASE) { repository.add(id, qty) }

suspend fun add(id: String, qty: Int) =
    traced("CartRepository.add", SpanCategory.REPOSITORY) { withContext(io) { remote.add(id, qty) } }
```

4. **State emissions** (optional but useful for "did the UI update"): `recordEvent("CartUiState.Success", SpanCategory.STATE_EMISSION)` after the state is set. Use `SpanCategory.UI_RENDER` only for a real render checkpoint.

## Choosing categories

`UI_INTERACTION`, `VIEW_MODEL`, `USE_CASE`, `REPOSITORY`, `NETWORK` (adapter does this), `BUSINESS_VALIDATION`, `DATABASE` (adapter does this), `STATE_EMISSION`, `UI_RENDER`, `STATE`, `NAVIGATION`, `LIFECYCLE`, `CUSTOM`.

## Gotchas

- Journey context travels in the coroutine context. `withContext(dispatcher)` keeps it; `GlobalScope.launch` or a bare `scope.launch` inside the block does **not** — use `launchInJourney(journey)` or `withContext(journey.context())`.
- Callback APIs: `val span = journey.beginSpan("name", category)` … `span.end()` / `span.fail(t)`; always end it.
- Exceptions are recorded then rethrown unchanged; cancellation is recorded `CANCELLED`. Do not catch just to trace.
- Metadata values are `String`/`Int`/`Long`/`Double`/`Boolean` only. No bodies, tokens or PII; keys containing sensitive fragments are masked automatically but do not rely on it.
- Do not start a journey per network call or per recomposition; one journey per user-visible operation.

## Verify

A trace named after the flow with nested USE_CASE → REPOSITORY (→ NETWORK/DATABASE) spans appears in the recorder; see [verification](../verification.md).
