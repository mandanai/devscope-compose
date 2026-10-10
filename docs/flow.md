# Flow, state and lifecycle tracing (`devscope-flow`)

Correlates Kotlin `Flow`, `StateFlow` and `SharedFlow` activity with a journey. Everything runs in `commonMain`; only `LifecycleOwner.asLifecycleSource()` lives in `androidMain`.

```kotlin
flow.traceFlow("products-flow")                          // cold flow
val traced = cartState.traceState("cart-ui-state")        // StateFlow wrapper
cartState.observeWithJourneyScope("cart-ui-state", options) { state = it }   // terminal collector
```

## Which journey receives events
1. The collecting coroutine's ambient journey (`TraceContext`), if any.
2. Otherwise `FlowTraceOptions.journey`, evaluated per event. UI collectors usually have no ambient journey, so pass a provider such as `viewModel::observedJourney`.
3. Neither: nothing is recorded and the flow behaves exactly as before.

## What is automatically observed
| Event (`state.event`) | Recorded when |
| --- | --- |
| `started` / `completed` / `failed` / `cancelled` | A `traceFlow` collection begins or ends. |
| `upstream` | A value reaches the `traceFlow` operator. |
| `observed` | A value is delivered to the collector at the instrumentation point (`traceFlow`, `traceState`, `traceShared`). Carries `state.previous`, `state.current`, `state.changed`. |
| `collector-added` / `collector-removed` | A collector of a traced `StateFlow`/`SharedFlow` starts or stops; `state.collectors` is the active count, `state.end` is `completed`, `cancelled` or `failed`. |
| lifecycle (`lifecycle.state`) | A `LifecycleSource` is passed in `FlowTraceOptions.lifecycle` and the collector's lifecycle changes. |

## What needs manual instrumentation
- Wrapping each stream you care about (`traceFlow`, `traceState`, `traceShared`).
- Coroutine boundaries: only `launchTraced`/`asyncTraced` report job creation, completion, cancellation and failure, and propagate the journey context. Plain `launch` is invisible.
- Passing a journey provider for collectors that run outside a journey.
- Passing a `LifecycleSource`. On Android: `LifecycleOwner.asLifecycleSource()` (collect on the main thread). iOS has no adapter; collection works without lifecycle events. No Android lifecycle dependency exists outside `androidMain`.

## Limits
- `StateFlow` conflates equal and rapid values **before** a wrapper can see them. Only values delivered to a collector are reported; the SDK never claims to have seen a value it did not observe.
- Emission, collection, recomposition and on-screen presentation are different events. An `observed` event does not prove a recomposition or a frame; use an explicit render checkpoint (a `UI_RENDER` span) for that. The inspector shows the next render checkpoint after an observation as `ui.checkpoint`.
- A failure recorded by `traceFlow` is observed at that point and may originate downstream of it; only the exception type is recorded, never its message.

## Value safety
Values are never serialized. `ValueFormatter` (default: type name only) produces a summary truncated to `maxSummaryLength`. A formatter that throws is replaced by `<unformattable>`. Metadata keys pass through the core sanitizer.
