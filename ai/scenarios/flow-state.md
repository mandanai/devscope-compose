# Flow, StateFlow and coroutine tracing

**Module:** `devscope-flow` (Android + iOS; `asLifecycleSource()` is Android-only). Package `dev.chayaphon.journeyscope.flow`.

## Edits

Wrap only the streams the user cares about:

```kotlin
val uiState: StateFlow<CartUiState> = mutable.asStateFlow().traceState("cart-ui-state", FlowTraceOptions(journey = ::observedJourney))
flow.traceFlow("products-flow")
shared.traceShared("cart-events")
```

Terminal collector form: `state.observeWithJourneyScope("cart-ui-state", options) { render(it) }`.

Launch work that must show job completion/cancellation and carry the journey:

```kotlin
scope.launchTraced("load-cart", journey) { … }
val d = scope.asyncTraced("price", journey) { … }
```

## Which journey receives events

1. The collecting coroutine's ambient journey.
2. Else `FlowTraceOptions.journey` (evaluated per event). UI collectors usually have no ambient journey, so pass a provider such as `viewModel::observedJourney` that returns the latest journey.
3. Else nothing is recorded and the flow behaves exactly as before.

## Android lifecycle

Pass `lifecycle = owner.asLifecycleSource()` in `FlowTraceOptions` to record `lifecycle.state` (collect on the main thread). iOS has no adapter; omit it.

## Limits to tell the user

- `StateFlow` conflates equal/rapid values before the wrapper sees them; only delivered values are reported.
- Plain `launch` is invisible; only `launchTraced`/`asyncTraced` report job boundaries.
- Values are summarised by `ValueFormatter` (default: type name, `maxSummaryLength` 80). Do not supply a formatter that prints user data.

## Verify

Events `started`/`observed` (and `state.previous/current/changed`) appear on the journey's trace.
