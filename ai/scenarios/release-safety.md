# Release safety

JourneyScope ships disabled. The goal is that release builds neither collect data nor contain debug UI.

## Rules

1. Configure with `JourneyScopeConfig.debugOnly(isDebuggable = <real debug signal>)`.
   - Android: `(applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0` or `BuildConfig.DEBUG`.
   - iOS: Swift `#if DEBUG` passes `true`/`false` into `MainViewController(isDebug:)`.
2. `devscope-android`, `devscope-ui-compose`, `devscope-export` → `debugImplementation` (Android) or a debug-only framework variant (iOS). Only `devscope-core` and adapters stay in release.
3. With the real core and `enabled = false` the disabled path is a flag check per call. That is the recommended strategy when adapters are used.

## Core-only apps: swap in the noop

```kotlin
debugImplementation("dev.chayaphon.journeyscope:devscope-core:<version>")
releaseImplementation("dev.chayaphon.journeyscope:devscope-core-noop:<version>")
```

Only valid when **no adapter** is used from main sources. Adapters depend on the real core; Gradle fails with a capability conflict if both are present. The noop `configure(config)` takes no recorder, so recorder/persistence wiring must live in debug code.

## iOS

Build the Kotlin framework in two variants, or pass `isDebug = false` so the SDK stays disabled. Reference the inspector entry from Swift under `#if DEBUG`.

## ProGuard / R8

No keep rules are shipped. R8 full-mode minification has not been verified on a release build of the sample; if the user minifies, tell them and run a release build.

Details: [docs/release.md](../../docs/release.md).

## Verify

`./gradlew :<app>:dependencies --configuration releaseRuntimeClasspath` shows no `devscope-ui-compose`, `devscope-export`, or `devscope-android`.
