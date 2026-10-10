# Architecture

## Dependency direction
```
samples/*  ->  devscope-ui-compose  ->  devscope-core
samples/*  ->  adapters (future)    ->  devscope-core
```
- `devscope-core` depends on nothing but Kotlin stdlib and kotlinx-coroutines. No Compose, Android or UIKit.
- Adapters (Ktor, OkHttp, Flow, Navigation, SQLDelight) depend only on core, never on each other or on the UI.
- Gradle rejects project cycles; check core purity with `./gradlew :devscope-core:dependencies`.

## Source sets
`commonMain`/`commonTest` by default; `androidMain`/`iosMain` only where a platform API forces it. Targets: `android`, `iosArm64`, `iosSimulatorArm64`.

## Build
- Convention plugins in `build-logic/convention` (`journeyscope.kmp.library`, `journeyscope.compose`, `journeyscope.publishing`, `journeyscope.sample.*`).
- Versions only in `gradle/libs.versions.toml`.
- SDK modules use `explicitApi()`, treat warnings as errors, and compile at Kotlin language/API 2.1 (`kotlinLanguage` in the catalog).
- AGP 9 forbids combining the KMP plugin with `com.android.application`, so the Android sample is split: `samples/composeApp` (KMP library) and `samples/androidApp` (application).

## Principles
Offline-first, no mandatory backend, no DI framework required, disabled by default (`JourneyScope.isEnabled`), secure-by-default data collection (to be enforced as features land).
