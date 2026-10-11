# Instrumentation

All adapters are optional and independent; each depends only on `devscope-core`.

## Operations
```kotlin
JourneyScope.withJourney("Checkout") {
    traceSuspend("ValidateCart", SpanCategory.USE_CASE) { validate() }
    recordEvent("Pay tapped", SpanCategory.UI_INTERACTION)
}
```
Cancellation is recorded as `CANCELLED` and rethrown. A throwable ends the span `FAILED` and is rethrown. Failure messages are not stored unless `RedactionPolicy.includeFailureMessages` is set.

## Coroutine propagation
Journeys travel in the coroutine context. `launchTraced` / `asyncTraced` (flow module) and the Ktor plugin read it; `currentTraceContext()` gives any suspend code the active journey. Dispatcher hops and `withContext` keep the context. Threads that are not coroutines (OkHttp) use an explicit tag instead.

## Network
- Ktor: `install(JourneyScopeNetwork) { trace = NetworkTraceConfig(...) }` (Android and iOS).
- OkHttp: `JourneyScopeInterceptor()` (Android only); tag requests with `journeyContext()`.
- Headers: `Authorization`, `Cookie`, `Set-Cookie`, `X-Api-Key` and similar are always redacted; other headers are recorded only if allow-listed. Query values are redacted unless allow-listed, opaque path segments become `{id}`.
- Bodies are never stored. An optional `BusinessResponseValidator` can inspect a bounded snippet to classify a 200 response as a business failure.

## Flow / StateFlow
`flow.traceFlow(name, options)`, `stateFlow.traceState(name)`, `sharedFlow.traceShared(name)` record emissions as events on the active journey. Collection tied to the Android lifecycle uses `asLifecycleSource()`.

## Navigation
`NavigationTracer` records `navigate`, `pop`, `replace`, `switchStack` and destination enter/exit. It is library-neutral: call it from your navigator.

## Database (SQLDelight)
`DatabaseTracing` wraps queries, writes and transactions in `DATABASE` spans; a transaction's child operations share one correlation id. Metadata holds the operation name, not SQL text or bound values.

## Expectations
`ExpectedSequence` declares the steps a journey should show; the Inspector and diagnostics report missing or out-of-order steps. A sequence applies only to journeys matched by `appliesTo`.

## Platform support
| Adapter | Android | iOS |
|---|---|---|
| core, diagnostics, export, ui-compose, navigation | yes | yes |
| network-ktor, flow, database-sqldelight | yes | yes |
| network-okhttp | yes | no |
| `asLifecycleSource()` | yes | n/a |
