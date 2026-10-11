# Setup playbook

Follow in order. Each step: **detect → act → check**. Report anything skipped and why. Stop and ask the user only where marked **ASK**.

## 0. Preconditions

- Android minSdk 26, compileSdk 37, JDK 17. iOS: Xcode 26+, Apple Silicon (`iosArm64`, `iosSimulatorArm64` only).
- JourneyScope is **pre-release**. Group `dev.chayaphon.journeyscope`, version `0.1.0`. If it is not resolvable, **ASK** whether to publish locally: from a checkout of this repo run `./gradlew publishToMavenLocal`.

## 1. Detect the project shape

Use [decision-tree.md](decision-tree.md). Record: platforms (Android only / KMP / iOS-hosted), module layout, dependency style (version catalog vs inline), DI, HTTP client, database, navigation, state holders, and the exact flow the user named.

Check: you can state in one sentence which modules you will add and which scenario files you will use.

## 2. Add repository and dependencies

- Add `mavenLocal()` to the repositories that resolve dependencies (first, ahead of `google()`/`mavenCentral()`), only while pre-release.
- Add dependencies through the project's own mechanism. If `gradle/libs.versions.toml` exists, add one `journeyscope` version entry and library aliases there; do not mix inline strings into a catalog-based build.
- Android app:
  ```kotlin
  implementation("dev.chayaphon.journeyscope:devscope-core:<version>")
  debugImplementation("dev.chayaphon.journeyscope:devscope-android:<version>")
  ```
- KMP shared module (`commonMain`): `devscope-core`, plus `devscope-ui-compose` and `devscope-export` only where the Inspector is embedded (iOS, or custom placement). Keep UI/export out of release: see [scenarios/release-safety.md](scenarios/release-safety.md).
- Add only the adapters the decision tree selected, each in the module that owns the instrumented code.

Check: `./gradlew help` (or the project's sync equivalent) resolves the new coordinates.

## 3. Configure exactly once

Android — in the `Application` subclass (create one and register it in the manifest only if none exists):

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        JourneyScope.configure(
            JourneyScopeConfig.debugOnly(isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0),
        )
    }
}
```

iOS / KMP — configure in the shared entry point and let Swift decide the flag:

```kotlin
fun MainViewController(isDebug: Boolean): UIViewController {
    JourneyScope.configure(JourneyScopeConfig.debugOnly(isDebuggable = isDebug))
    return ComposeUIViewController { App() }
}
```

Swift passes `#if DEBUG` → `true`, otherwise `false`. See [scenarios/release-safety.md](scenarios/release-safety.md).

Check: `JourneyScope.configure` appears exactly once in the repo and not in a library module. If the app already calls it, extend that call; never add a second one.

## 4. Open the Inspector

- Android with `devscope-android`: nothing to write; a debug-only ongoing notification opens it.
- iOS or custom placement: embed `JourneyScopeInspector(onClose = { … })` behind a debug-only entry (debug menu item or shake). The SDK defines no gesture.

## 5. Instrument

Start with the flow the user named. Instrument top-down so spans nest: journey at the user action → use case → repository → adapters. Use [scenarios/trace-usecase-flow.md](scenarios/trace-usecase-flow.md), then the adapter scenarios the decision tree selected. Prefer a small host helper (`traced(...)`) over repeating boilerplate.

Check: every `withJourney`/`startJourney` has a name a developer would recognise ("Add to Cart", not "journey1").

## 6. Verify

Run [verification.md](verification.md). Do not report done until its criteria are met or each unmet one is explained.

## 7. Report

Tell the user: modules added, files changed, flows instrumented, what you did **not** trace (and the manual step if any), how to open the Inspector, and any pre-release caveat.
