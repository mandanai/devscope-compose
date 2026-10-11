# Samples

| Module | Role |
| --- | --- |
| `composeApp` | Shared Compose Multiplatform shop sample. Uses only `core`, `flow`, `navigation`, `network-ktor` and `database-sqldelight` in shared code. |
| `androidApp` | Android host. Same code in debug and release; only its dependencies differ. |
| `iosApp` | Xcode host for `composeApp`; its Kotlin side (`iosMain`) adds the Inspector and export. |

## Debug vs release (Android)
`androidApp/build.gradle.kts` is the only place the split is decided, Chucker style:

- `debugImplementation`: the real modules plus `devscope-android` (Inspector via notification).
- `releaseImplementation`: the `-noop` twin of each module.

`composeApp` is compiled against the real modules; a Gradle rule in `androidApp` makes release resolve their twins instead. `scripts/check-noop-abi.sh` verifies the twins still expose every member the sample calls.

## Where things live in `composeApp`
- `App.kt`: screens and routing; the Inspector buttons appear only when a `SampleInspector` is supplied (iOS does, Android does not).
- `shop/`: `data`, `domain`, `ui`, and `SampleGraph` (object graph).
- `navigation/`: `SampleNavigator`.
- `iosMain`: `IosInspector`, `SampleExport`, `AddToCartCompletion` (debug tooling, never in shared code).
- Overhead benchmark lives in `commonTest/.../bench`.

## Run
- Android: `./gradlew :samples:androidApp:installDebug` or `installRelease` (release needs a signing config or manual signing).
- iOS: `./gradlew :samples:composeApp:linkDebugFrameworkIosSimulatorArm64`, then open `iosApp/iosApp.xcodeproj`.
