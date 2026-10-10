# Android integration

Minimum SDK 26, compile SDK 37, JDK 17. Artifacts are Kotlin Multiplatform; Gradle resolves the Android variant automatically.

## Standard application

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("dev.journeyscope:devscope-core:<version>")
    debugImplementation("dev.journeyscope:devscope-ui-compose:<version>")
}
```

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        JourneyScope.configure(JourneyScopeConfig.debugOnly(isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0))
    }
}
```

## Multi-module application

- Put `devscope-core` in the module that owns app startup and in every module that opens journeys (use `api` from a shared `:core` module if many do).
- Adapters belong in the module that owns the instrumented client, repository or ViewModel.
- Keep `devscope-ui-compose` and `devscope-export` in the app module (or a `:debug-tools` module) as `debugImplementation`.
- Call `JourneyScope.configure` exactly once, from the application module.

## Existing OkHttp client

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(JourneyScopeInterceptor())      // one span per call
    .build()
```

OkHttp runs interceptors on its own threads, so the journey is taken from a tag on the request. Tag requests made inside a journey with `Request.Builder.journeyContext()`. An untagged request follows `NetworkTraceConfig.noJourneyPolicy`. Use `addNetworkInterceptor` for one span per redirect or retry hop.

## Existing Ktor client

```kotlin
val client = HttpClient(engine) {
    install(JourneyScopeNetwork) {
        trace = NetworkTraceConfig()
    }
}
```

The Ktor plugin runs in common code, so the same call works on Android and iOS.

## Jetpack Compose

```kotlin
setContent {
    var showInspector by remember { mutableStateOf(false) }
    if (showInspector) JourneyScopeInspector(onClose = { showInspector = false }) else AppContent(onOpenInspector = { showInspector = true })
}
```

Pass `asLifecycleSource()` (from `devscope-flow`) to your root composable when you want flow collection tied to the Android lifecycle, as the sample does.

## Debug-only installation

```kotlin
debugImplementation("dev.journeyscope:devscope-ui-compose:<version>")
debugImplementation("dev.journeyscope:devscope-export:<version>")
implementation("dev.journeyscope:devscope-core:<version>")
```

Create the inspector, `JourneyPersistence` and `JourneyExportController` from `src/debug/` and provide an empty counterpart in `src/release/`.

## Release-disabled configuration

Two supported strategies:

1. **Keep core, stay disabled (recommended with adapters).** `JourneyScopeConfig.debugOnly(isDebuggable = false)` leaves the SDK off. The disabled path is a flag check per call.
2. **Swap in `devscope-core-noop`** for apps that only use the core facade: `debugImplementation(core)`, `releaseImplementation(core-noop)`. Adapters depend on the real core, so they cannot coexist with the no-op artifact (Gradle fails with a capability conflict, by design). See [release](release.md).

## ProGuard / R8

No keep rules are shipped. The sources use no reflection by name (the OkHttp adapter only uses a class literal as a request tag key), but R8 full-mode minification has not been verified on a release build of the sample.
