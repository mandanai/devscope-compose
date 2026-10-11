# iOS integration

JourneyScope is consumed from a Kotlin Multiplatform module and surfaced to the host app through the framework that module produces. Targets: `iosArm64` and `iosSimulatorArm64`. `iosX64` (Intel simulator) is not built.

## Framework integration

1. Add the dependencies to the shared module's `commonMain` (the SDK is KMP, so the iOS variants resolve automatically):

```kotlin
kotlin {
    sourceSets.commonMain.dependencies {
        implementation("dev.chayaphon.journeyscope:devscope-core:<version>")
        implementation("dev.chayaphon.journeyscope:devscope-ui-compose:<version>")
        implementation("dev.chayaphon.journeyscope:devscope-export:<version>")
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework { baseName = "ComposeApp"; isStatic = true }
    }
}
```

2. Embed the framework in the Xcode project with a build phase that runs `./gradlew :<module>:embedAndSignAppleFrameworkForXcode`, as `samples/iosApp` does.

## Shared Compose UI

`JourneyScopeInspector` is common Compose code; it renders identically from `ComposeUIViewController`.

```kotlin
fun MainViewController(isDebug: Boolean): UIViewController {
    JourneyScope.configure(JourneyScopeConfig.debugOnly(isDebuggable = isDebug))
    return ComposeUIViewController { App() }
}
```

Let Swift decide the flag so release builds stay off (see below).

## Deployment target

Follow the Compose Multiplatform minimum for your version (iOS 13+ at the time of writing); the SDK adds no higher requirement.

## Debug-only initialization

```swift
#if DEBUG
let isDebug = true
#else
let isDebug = false
#endif
// ContentView / UIViewControllerRepresentable
MainViewControllerKt.MainViewController(isDebug: isDebug)
```

Note: `samples/iosApp` does not gate on `#if DEBUG`; it enables the SDK unconditionally because it is a demo. For release, either build a second framework variant without `devscope-ui-compose` / `devscope-export`, or pass `isDebug = false` so the SDK stays disabled.

## Opening the Inspector

The sample opens it through in-app navigation (a button on the product screen pushes the inspector route). The SDK does not define a gesture. Common choices are a debug menu item or a shake handler that toggles `showInspector` in the shared Compose root.

## Capability support

| Capability | iOS |
|---|---|
| Core tracing, coroutine context propagation | Supported |
| Compose Inspector | Supported |
| Export / import / on-device persistence | Supported (file store + share sheet via `IosShareSink`) |
| Ktor network tracing | Supported |
| **OkHttp interceptor** | **Not available** (Android-only module) |
| Flow / StateFlow tracing | Supported |
| Android lifecycle source (`asLifecycleSource`) | Not applicable |
| Navigation tracer | Supported (SDK-neutral events) |
| SQLDelight tracing | Supported with the native driver |
| Diagnostics | Supported |

## Limitations
- Intel simulators are not covered; use an Apple Silicon Mac.
- Verification status: the iOS framework compiles and common tests run on `iosSimulatorArm64` in CI (macOS runner). See [ui-verification](ui-verification.md) for what has and has not been checked on a device.
