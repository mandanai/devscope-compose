# Release safety and disabled mode

## Disabled by default
`JourneyScope` ships disabled. While `enabled = false` every journey is an inert singleton, no recorder entry is created, `JourneyPersistence.install` returns a plain in-memory recorder without invoking your file-system factory, and the export controller has nothing to persist. Use `JourneyScopeConfig.debugOnly(isDebuggable)` so release builds stay off.

## Keep the inspector and export out of release
Only host-side instrumentation needs to remain in release; the UI and export modules are debug tools.

Android (`app/build.gradle.kts`):
```kotlin
dependencies {
    debugImplementation(project(":devscope-ui-compose"))   // inspector UI + export
    debugImplementation(project(":devscope-export"))
    implementation(project(":devscope-core"))               // setup B: real core with adapters, disabled in release
}
```
Create the inspector, `JourneyPersistence` and `JourneyExportController` from `src/debug/` code (for example a `DebugTools` object exposing a composable) and provide an empty counterpart in `src/release/`.

iOS: build the Kotlin framework in two variants. The release framework exports only the app code (and core or core-noop); the debug framework also links the UI and export modules. Reference the inspector entry point from Swift under `#if DEBUG`.

## Two supported release setups
- **A. Chucker style: same app code, noop in release.** Swap every module the app code touches for its `-noop` twin by build type. Working example: `samples/androidApp` (identical UI in both builds).
- **B. Real core, disabled in release.** Keep `devscope-core` and adapters in every variant and use `JourneyScopeConfig.debugOnly(isDebuggable)` so release stays off. Choose this when app code uses APIs that have no noop twin (diagnostics, export, the Inspector).

## Setup A: `-noop` twins
```kotlin
dependencies {
    debugImplementation("dev.chayaphon.journeyscope:devscope-core:<version>")
    debugImplementation("dev.chayaphon.journeyscope:devscope-flow:<version>")
    debugImplementation("dev.chayaphon.journeyscope:devscope-android:<version>")   // Inspector via notification
    releaseImplementation("dev.chayaphon.journeyscope:devscope-core-noop:<version>")
    releaseImplementation("dev.chayaphon.journeyscope:devscope-flow-noop:<version>")
}
```
Twins exist for `core`, `flow`, `navigation`, `network-ktor` and `database-sqldelight`. Each publishes the same Gradle capability as its real module, so Gradle refuses both on one classpath.

When a library module (such as the sample's `composeApp`) brings the real modules transitively, tell Gradle to prefer the twin in release:
```kotlin
configurations.matching { it.name.startsWith("release") }.configureEach {
    resolutionStrategy.capabilitiesResolution.all {
        candidates.firstOrNull { it.id.displayName.contains("-noop") }?.let { select(it) }
    }
}
```
That library is compiled against the real modules, so a twin must stay binary compatible with every member the library calls. `scripts/check-noop-abi.sh` enforces this in CI (adapter twins mirror the real ABI; the sample's compiled calls must all resolve).

Twins keep behaviour, not tracing: `observeWithJourneyScope` still collects, `DatabaseTracing.transaction` still runs its transaction, the Ktor plugin installs but intercepts nothing. Keep application behaviour independent of whether a journey is recording.

Limits:
- `devscope-ui-compose`, `devscope-export` and `devscope-diagnostics` have no twin; keep them out of shared code (`debugImplementation`, a debug source set, or an iOS-only source set).
- `JourneyScope.configure(config, recorder)` in the core twin ignores both arguments; recorder and persistence wiring belongs in debug code.
