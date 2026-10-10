# JourneyScope core API

## Enable
Collection is off by default. The host opts in:
```kotlin
JourneyScope.configure(JourneyScopeConfig.debugOnly(isDebuggable = BuildConfig.DEBUG))
```
`JourneyScopeConfig`: `enabled`, `maxTraces`, `maxEventsPerTrace`, `captureStackTraces`, `recordFailures`, `slowJourneyThreshold`, `samplingRate`, `maxMetadataSize`, `redactionPolicy`. Invalid values throw at construction.

## Record
```kotlin
JourneyScope.withJourney("Add to Cart") {              // COMPLETED / FAILED / CANCELLED, exceptions rethrown
    recordEvent("Button Click", SpanCategory.UI_INTERACTION)
    val cart = traceSuspend("AddToCartUseCase", SpanCategory.USE_CASE) {
        traceSuspend("CartRepository.add", SpanCategory.REPOSITORY) { repository.add(item) }
    }
    recordEvent("CartUiState.Success", SpanCategory.STATE_EMISSION)
}
```
Manual control: `val journey = JourneyScope.startJourney("x")` ... `journey.finish() | fail(t) | cancel() | interrupt(reason)`.

| Need | API |
| --- | --- |
| Suspend operation | `journey.traceSuspend(name, category) { }` (parent from coroutine context) |
| Sync operation | `journey.trace(name, category, parent = span) { span -> }` |
| Callback-style | `val span = journey.beginSpan(...)`, later `span.end()` / `span.fail(t)` |
| Manually launched work | `scope.launch(journey.context(parentSpan)) { }` or `scope.launchInJourney(journey, parentSpan) { }` |
| Instant checkpoint | `journey.recordEvent(name)` / `span.recordEvent(name)` |
| App going to background | `JourneyScope.interruptActive(reason)` |

Tracing never changes a block's return value. Exceptions are recorded then rethrown unchanged; `CancellationException` is recorded as CANCELLED and rethrown. SDK-internal errors are swallowed so they cannot replace application errors. Disabled or unsampled journeys are inert: blocks run directly.

## Read
- `JourneyScope.tracer.recorder.snapshot()` -> `List<JourneyTrace>` (spans/events ordered by time, then sequence)
- `recorder.events: SharedFlow<RecorderEvent>`; `InMemoryJourneyRecorder.observeTraces()` for full-snapshot updates
- `trace.timing()` -> inclusive/exclusive per span, gaps, critical path; `trace.summary` once finished

## Data safety
Metadata values are `String`/`Long`/`Double`/`Boolean` only (no `toString()` of arbitrary objects). Keys matching `RedactionPolicy.sensitiveKeyFragments` are replaced; total size is capped by `maxMetadataSize`. Failures record the exception class only; messages require `includeFailureMessages`, stack traces require `captureStackTraces`. Never put request bodies or credentials in metadata.

## Extension points (interfaces only)
`JourneyRecorder`, `JourneyExporter`, `JourneyStore`, `JourneyProcessor`.
