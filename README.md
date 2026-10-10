# JourneyScope

Runtime debugging and diagnostics SDK for Kotlin Multiplatform apps. Inspect complete application journeys (UI → ViewModel → UseCase → Repository → Network → Database → UI state), not only HTTP calls. Android and iOS, with a Compose Multiplatform inspector UI.

> Status: pre-release `0.1.0-SNAPSHOT`. Phases 0-8 are implemented: tracing engine, network/flow/navigation/database adapters, diagnostics, secure export, Compose inspector, local Maven publication and CI. Nothing has been published to a remote repository. See [docs/release-readiness.md](docs/release-readiness.md).

## Modules
| Module | Purpose | Android | iOS |
|---|---|---|---|
| `devscope-core` | Platform-independent tracing engine | yes | yes |
| `devscope-core-noop` | Release twin of the core facade (no adapters) | yes | yes |
| `devscope-ui-compose` | Compose Multiplatform Inspector | yes | yes |
| `devscope-network-ktor` | Ktor client plugin, [docs/network.md](docs/network.md) | yes | yes |
| `devscope-network-okhttp` | OkHttp interceptor | yes | no |
| `devscope-flow` | Flow / StateFlow / coroutine tracing, [docs/flow.md](docs/flow.md) | yes | yes |
| `devscope-navigation` | Navigation tracing, [docs/navigation.md](docs/navigation.md) | yes | yes |
| `devscope-database-sqldelight` | SQLDelight tracing, [docs/database.md](docs/database.md) | yes | yes |
| `devscope-diagnostics` | Evidence-based diagnostics, [docs/diagnostics.md](docs/diagnostics.md) | yes | yes |
| `devscope-export` | Redacted export, import, persistence, [docs/export.md](docs/export.md) | yes | yes |
| `samples/composeApp`, `androidApp`, `iosApp` | Add to Cart sample | yes | yes |

iOS targets: `iosArm64`, `iosSimulatorArm64` (no Intel simulator).

## Capabilities
| Capability | Android | iOS |
|---|---|---|
| Off by default, debug-only switch | yes | yes |
| Coroutine-context journey propagation | yes | yes |
| Sensitive data redacted by default (keys, headers, URLs, failures) | yes | yes |
| Ktor tracing | yes | yes |
| OkHttp tracing | yes | not available |
| StateFlow / Flow tracing | yes | yes |
| Lifecycle source | yes | n/a |
| SQLDelight tracing | yes | yes |
| Compose Inspector | yes | yes |
| Export / import / on-device history | yes | yes |

## Quick start
```
./gradlew :samples:androidApp:installDebug
```
iOS: open `samples/iosApp/iosApp.xcodeproj` in Xcode 26+ and run. See [docs/development.md](docs/development.md).

## Docs
- [Getting started](docs/getting-started.md)
- [Android integration](docs/android-integration.md) / [iOS integration](docs/ios-integration.md)
- [Instrumentation](docs/instrumentation.md), [Diagnostics](docs/diagnostics.md)
- [Privacy and security](docs/privacy-security.md), [Persistence and export](docs/export.md)
- [Architecture](docs/architecture.md), [Modules](docs/modules.md), [API](docs/api.md)
- [Performance](docs/performance.md), [Publishing](docs/publishing.md), [Release safety](docs/release.md)
- [Development](docs/development.md), [Sample walkthrough](docs/sample-walkthrough.md)
- [Changelog](CHANGELOG.md), [Contributing](CONTRIBUTING.md)

License: Apache-2.0 (see [LICENSE](LICENSE)).
