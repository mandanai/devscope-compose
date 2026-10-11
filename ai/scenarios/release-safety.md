# Release safety

JourneyScope ships disabled. The goal is that release builds neither collect data nor contain debug UI.

## Rules

1. Configure with `JourneyScopeConfig.debugOnly(isDebuggable = <real debug signal>)`.
   - Android: `(applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0` or `BuildConfig.DEBUG`.
   - iOS: Swift `#if DEBUG` passes `true`/`false` into `MainViewController(isDebug:)`.
2. `devscope-android`, `devscope-ui-compose`, `devscope-export` → `debugImplementation` (Android) or a debug-only framework variant (iOS). Only `devscope-core` and adapters stay in release.
3. With the real core and `enabled = false` the disabled path is a flag check per call. That is the recommended strategy when adapters are used.

## Same code, noop in release

Swap each module the app code touches for its `-noop` twin (`core`, `flow`, `navigation`, `network-ktor`, `database-sqldelight`):

```kotlin
debugImplementation("dev.chayaphon.journeyscope:devscope-core:<version>")
releaseImplementation("dev.chayaphon.journeyscope:devscope-core-noop:<version>")
```

Working example: `samples/androidApp`. The twins keep app behaviour (flows still collect, transactions still run) and only drop tracing. They publish the real module's Gradle capability, so Gradle fails if both are on one classpath. Keep `devscope-ui-compose`, `devscope-export` and `devscope-diagnostics` out of shared code: they have no twin. The core twin's `configure(config, recorder)` ignores both arguments, so recorder/persistence wiring must live in debug code.

## iOS

Build the Kotlin framework in two variants, or pass `isDebug = false` so the SDK stays disabled. Reference the inspector entry from Swift under `#if DEBUG`.

## ProGuard / R8

No keep rules are shipped. R8 full-mode minification has not been verified on a release build of the sample; if the user minifies, tell them and run a release build.

Details: [docs/release.md](../../docs/release.md).

## Verify

`./gradlew :<app>:dependencies --configuration releaseRuntimeClasspath` shows no `devscope-ui-compose`, `devscope-export`, or `devscope-android`.
