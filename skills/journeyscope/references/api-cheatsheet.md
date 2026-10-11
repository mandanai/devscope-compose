<!-- Generated from ai/api-cheatsheet.md by scripts/sync-skill-references.sh; edit there. -->
# API cheatsheet

The only APIs an agent may use. Signatures are checked against `devscope-*/api/*.api` and sources. Package prefix `dev.chayaphon.journeyscope.`. If something is not here, do not use it.

## devscope-core (`core`)

```kotlin
JourneyScope.configure(config: JourneyScopeConfig, recorder: JourneyRecorder? = null): JourneyTracer
JourneyScope.disable()
JourneyScope.isEnabled: Boolean
JourneyScope.tracer: JourneyTracer              // .recorder.snapshot(): List<JourneyTrace>, .config
JourneyScope.startJourney(name, metadata = DiagnosticMetadata.Empty): Journey
suspend JourneyScope.withJourney(name, metadata = Empty, block: suspend Journey.() -> T): T
JourneyScope.interruptActive(reason: String)

JourneyScopeConfig.debugOnly(isDebuggable: Boolean)
JourneyScopeConfig(enabled = false, maxTraces = 50, maxEventsPerTrace = 500, captureStackTraces = false,
    recordFailures = true, slowJourneyThreshold = 2.seconds, samplingRate = 1.0, maxMetadataSize = 1024,
    redactionPolicy = RedactionPolicy.Default, maxEventsPerSecond: Int? = null)

// Journey (receiver inside withJourney)
suspend traceSuspend(name, category = SpanCategory.CUSTOM, metadata = Empty, block: suspend () -> T): T
trace(name, category, metadata, parent: SpanHandle? = null, block: (SpanHandle) -> T): T
beginSpan(name, category, metadata, parent): SpanHandle      // SpanHandle.end(status?, metadata?) / fail(t) / recordEvent(...)
recordEvent(name, category = ..., metadata = ..., parent = null)
context(parent: SpanHandle? = null): CoroutineContext
finish() / fail(t) / cancel(reason?) / interrupt(reason) / snapshot(): JourneyTrace?

suspend currentTraceContext(): TraceContext?     // .journey, .spanId, .recordEvent(...), .beginChildSpan(...)
CoroutineScope.launchInJourney(journey, parent: SpanHandle? = null, context, start, block): Job

diagnosticMetadata { put(key, String | Long | Int | Double | Boolean) }
SpanCategory.{UI_INTERACTION, VIEW_MODEL, USE_CASE, REPOSITORY, NETWORK, BUSINESS_VALIDATION, DATABASE,
    STATE_EMISSION, UI_RENDER, STATE, NAVIGATION, LIFECYCLE, CUSTOM}
TraceStatus, SpanStatus (enums)
JourneyTrace(id, name, status, spans, events, failure, metadata, summary, ...)   // .timing()

ExpectedSequence(name, steps: List<ExpectedStep>, appliesTo = { true }).evaluate(trace): SequenceCheck   // .missing
ExpectedStep.named(label, namePrefix) / ExpectedStep.category(label, category, namePrefix = "")

RedactionPolicy(sensitiveKeyFragments, replacement = "[REDACTED]", includeFailureMessages = false)
```

## devscope-core network types (`core.network`)

```kotlin
NetworkTraceConfig(...)      // headerAllowlist, queryParamAllowlist, bodyCapture, businessValidator, noJourneyPolicy, propagation
BodyCapture(maxBytes = ...)  // opt-in, capped at 4 KiB
fun interface BusinessResponseValidator { fun inspect(request: NetworkRequestInfo, response: NetworkResponseInfo): BusinessResult? }
BusinessResult(status: BusinessStatus, code: String?)    // BusinessStatus.SUCCESS / FAILURE / SESSION_EXPIRED / ...
PropagationConfig(trustedHosts, requireHttps = true)
```

## devscope-network-ktor (`network.ktor`)

```kotlin
HttpClient { install(JourneyScopeNetwork) { trace = NetworkTraceConfig(); tracer = { JourneyScope.tracer } } }
```

## devscope-network-okhttp (`network.okhttp`, Android)

```kotlin
JourneyScopeInterceptor()                              // or JourneyScopeInterceptor(config: NetworkTraceConfig)
Request.Builder.journeyContext(context: TraceContext?)
suspend Request.Builder.tagWithCurrentJourney()
```

## devscope-flow (`flow`)

```kotlin
Flow<T>.traceFlow(name, options: FlowTraceOptions<T> = FlowTraceOptions())
StateFlow<T>.traceState(name, options): StateFlow<T>
SharedFlow<T>.traceShared(name, options): SharedFlow<T>
suspend StateFlow<T>.observeWithJourneyScope(name, options, onEach: suspend (T) -> Unit): Nothing
CoroutineScope.launchTraced(name, journey: Journey? = null, context, start, block): Job
CoroutineScope.asyncTraced(name, journey, context, start, block): Deferred<T>
FlowTraceOptions(formatter, maxSummaryLength = 80, recordUpstream = true, recordObserved = true,
    journey: (() -> Journey?)? = null, lifecycle: LifecycleSource? = null)
LifecycleOwner.asLifecycleSource(): LifecycleSource      // Android only
```

## devscope-navigation (`navigation`)

```kotlin
NavigationTracer(NavigationTraceConfig(routeRedactor = RouteRedactor.Default, maxStackDepth = 16, journey = null))
  .navigate(from, to, backStackBefore, backStackAfter, journey = null): Boolean
  .pop(from, to, backStackBefore, backStackAfter, journey = null)
  .replace / .switchStack / .destinationEntered / .destinationExited / .snapshot / .markCompleted(destination)
```

## devscope-database-sqldelight (`database.sqldelight`)

```kotlin
DatabaseTracing(DatabaseTraceConfig())
suspend query / insert / update / delete(label, rowCount: ((T) -> Long)? = null, block: () -> T): T
suspend transaction(transacter, label) { query/insert/update/delete/transaction/rollback(value) }
```

## devscope-ui-compose (`ui`)

```kotlin
@Composable JourneyScopeInspector(modifier, source, thresholds, analyzer, metadataPolicy, colors,
    expectedSequences: List<ExpectedSequence> = emptyList(), onClose: (() -> Unit)? = null,
    exportController: JourneyExportController? = null)
```

## devscope-export (`export`)

```kotlin
ExportPolicy(persistenceEnabled = false, allowBodies = false, retention = 7.days, maxStoredTraces = 100, ...)
JourneyPersistence.install(config, policy, fileSystem: () -> TraceFileSystem, ...): JourneyPersistence   // .recorder, .store, flush(), close()
JourneyExportController(recorder, store, policy, shareSink, ...)
createTraceFileSystem(directory: String)            // storage package; Android: androidTraceDirectory(context)
AndroidShareSink(context, uriFor = null) / IosShareSink()
```

## devscope-android (`android`)

```kotlin
JourneyScopeAndroid.notificationsEnabled: Boolean
JourneyScopeAndroid.exportController: JourneyExportController?
JourneyScopeAndroid.launch(context) / intent(context)
```

## devscope-core-noop

Same facade names as `devscope-core` (`JourneyScope`, `Journey`, `SpanHandle`, `JourneyScopeConfig`, `RedactionPolicy`, `DiagnosticMetadata`, `SpanCategory`, ...); every call is inert. No recorder parameter on `configure`.
