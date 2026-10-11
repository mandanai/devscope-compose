# Getting started

The mandatory integration is two calls: configure once, then open a journey around the operation you want to see.

## 1. Add the core

```kotlin
// build.gradle.kts of the module that owns the app wiring
dependencies {
    implementation("dev.chayaphon.journeyscope:devscope-core:<version>")
}
```

Published coordinates use group `dev.chayaphon.journeyscope`; see [publishing](publishing.md) for local use before a release exists.

## 2. Initialize (debug only)

```kotlin
JourneyScope.configure(JourneyScopeConfig.debugOnly(isDebuggable = BuildConfig.DEBUG))
```

`JourneyScope` ships disabled. While disabled every journey is an inert singleton and nothing is recorded.

## 3. Instrument an operation

```kotlin
JourneyScope.withJourney("Add to Cart") {
    traceSuspend("AddToCartUseCase", SpanCategory.USE_CASE) { repository.add(item) }
}
```

Inside the block the journey is carried by the coroutine context. Code deeper in the stack joins it with `currentTraceContext()`, so no layer needs a `Journey` parameter.

## 4. Optional adapters

| Need | Module | Entry point |
|---|---|---|
| Ktor requests | `devscope-network-ktor` | `install(JourneyScopeNetwork)` |
| OkHttp requests (Android) | `devscope-network-okhttp` | `JourneyScopeInterceptor()` |
| StateFlow / Flow | `devscope-flow` | `traceFlow`, `traceState`, `traceShared`, `launchTraced` |
| Navigation | `devscope-navigation` | `NavigationTracer` |
| SQLDelight | `devscope-database-sqldelight` | `DatabaseTracing` |

See [instrumentation](instrumentation.md).

## 5. Open the Inspector

**Android:** add `debugImplementation(devscope-android)`. A notification opens the Inspector; no button, no export wiring (see [Android integration](android-integration.md)).

**iOS or custom placement:** embed the composable yourself:

```kotlin
JourneyScopeInspector(onClose = { showInspector = false })
```

It reads the global recorder by default. Pass `exportController` to enable history, import and sharing.

## 6. Export a safe report

Create `JourneyPersistence` and `JourneyExportController` (see [export](export.md)), pass the controller to the Inspector, and use Export. Reports go through a second redaction pass and are reviewed on screen before they are shared. Details in [privacy and security](privacy-security.md).

## Platform guides
- [Android](android-integration.md)
- [iOS](ios-integration.md)
