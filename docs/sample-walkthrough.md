# Sample walkthrough: Add to Cart

The sample (`samples/composeApp`, hosted by both the Android and iOS apps) is a small shopping feature whose every layer is instrumented with the public tracing API. Tapping **Add to Cart** records a real `JourneyTrace` that the Inspector reads from the same recorder (`JourneyScope.tracer.recorder`).

## State, lifecycle and navigation (Phase 5)
The product and cart screens collect `CartUiState` through `observeWithJourneyScope`, with the journey provider `ProductViewModel.observedJourney`. "Add & open cart" keeps the journey open, navigates to `CartScreen` via `SampleNavigator` (which feeds `NavigationTracer`), and `CartScreen` calls `markCompleted` after its first frame. The Android host passes `asLifecycleSource()` so collector lifecycle events appear; iOS passes none. The new events are events, not spans, so the existing span assertions are unchanged.

## Layers and instrumentation points
| Stage | Code | Category | How it is recorded |
| --- | --- | --- | --- |
| Tap | `ProductViewModel.addToCart` | `UI_INTERACTION` | explicit: `startJourney` + `recordEvent` in the click handler |
| View model | `ProductViewModel.execute` | `VIEW_MODEL` | explicit: `journey.traceSuspend` |
| Use case | `AddToCartUseCase` | `USE_CASE` | explicit: `traced(...)` |
| Repository | `CartRepository.add` | `REPOSITORY` | explicit: `traced(...)`, then `withContext(ioDispatcher)` |
| API (simulated) | `SimulatedCartApi` | `NETWORK` | explicit: `traced(...)` |
| Business validation | `CartRepository.validate` | `BUSINESS_VALIDATION` | explicit: `traced(...)` |
| Local storage (simulated) | `CartLocalDataSource.upsert` | `DATABASE` | explicit: `traced(...)` |
| State emission | `ProductViewModel.emit` | `STATE_EMISSION` | explicit: `recordEvent` after each `StateFlow` write |
| Render checkpoint | `ProductDetailScreen` → `onCartCountRendered` | `UI_RENDER` | explicit: span from emission to UI acknowledgement |

`traced` (in `SampleTracing.kt`) reads the journey from the coroutine context via `currentTraceContext()`, so layers never take a `Journey` parameter.

**Needs developer instrumentation:** every row above, plus starting and finishing the journey. **Automatic:** carrying the Trace ID and parent span across suspension and `withContext` dispatcher changes, monotonic timing, failure/cancellation classification inside `traceSuspend`, and delivery to the Inspector. **Not captured:** touch-down to click-handler latency, main-thread jank, and any work you do not wrap.

## Real, simulated, and fixture data
| Kind | What | Where |
| --- | --- | --- |
| Recorded at runtime | Every trace produced by the Add to Cart button; all durations come from the monotonic clock | Inspector |
| Simulated operations | The API call and the database write are `delay()`s (`LatencyProfile`) and are tagged `simulated=true`; the delay is the only hardcoded number, the recorded duration is still measured | `shop/data` |
| Deterministic fixtures | `DemoJourneys` static traces, opened with **Demo data** | `devscope-ui-compose` |

## Render checkpoint
The UI runs `LaunchedEffect(renderToken) { withFrameNanos { }; onCartCountRendered(token) }`. The effect starts after the composition holding the new state is applied, and `withFrameNanos` resumes at the start of the next frame. The `UI_RENDER` span therefore ends when **Compose has finished composition, layout and draw for the frame containing that state**. It does not prove the pixels reached the display, and a state emission alone proves nothing about drawing.

- Tokens increase monotonically; one acknowledgement also resolves earlier tokens, because Compose may draw only the latest of two quick emissions.
- If no acknowledgement arrives within 2 s (for example the product screen is not composed because the Inspector is open), the span ends `OK` with an event `Render checkpoint not confirmed` (`renderConfirmed=false`). The business outcome of the journey is unchanged. Stay on the product screen to see a confirmed render.

## Error handling contract
`CartRejectedException` and `SimulatedNetworkException` are caught only to show a failure state, then the journey is failed. Any other throwable fails the journey and is rethrown. Cancellation is recorded as `CANCELLED` and rethrown. Failures record the exception class only; messages and stack traces stay off by default.

## Reproducing each scenario
Open the app, pick a scenario under **Debug scenario**, tap **Add to Cart**, then open **Inspector** (a running trace shows a ticking duration).

| Scenario | Expected trace |
| --- | --- |
| Normal | `Completed`, about 150 ms, spans for every layer, cart count increases |
| Slow API | `Completed` and flagged `Slow Flow` (about 2.4 s > the 2 s threshold); the `POST /cart/items` span is highlighted |
| Business failure | `Failed`; `validateCartResponse` is `FAILED` (`CartRejectedException`), no database span; UI shows "Out of stock" |
| Network exception | `Failed`; `POST /cart/items` is `FAILED` (`SimulatedNetworkException`); no validation span; UI shows "Network unavailable" |
| Cancelled | `Cancelled` after about 300 ms (self-cancelled, or press **Cancel** during Slow API); the network span is `CANCELLED` |
| Concurrent | tap **Add to Cart** repeatedly during Slow API: each tap is its own trace with its own Trace ID |

## Tests
`./gradlew :samples:composeApp:testAndroidHostTest` (and `iosSimulatorArm64Test` with Xcode configured). Most tests use the test scheduler's virtual time as the journey clock, so recorded durations equal the configured delays exactly; `RealClockJourneyTest` runs on real dispatchers and the system monotonic clock.

## Network layer
The sample talks to a Ktor `HttpClient` backed by `MockEngine` (`shop/data/SampleHttpClient.kt`), so it stays offline while exercising the real `JourneyScopeNetwork` plugin. The plugin produces the NETWORK span (`POST api.shop.example/cart/items`); `CartBusinessValidator` maps the response `returnCode` to a business status shown on that span next to the HTTP status. Latency comes from a small `SimulatedLatency` plugin installed after the tracing plugin, because `MockEngine` handlers cannot share a test scheduler.
