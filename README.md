# JourneyScope

Runtime debugging and diagnostics SDK for Kotlin Multiplatform apps. Inspect complete application journeys (UI → ViewModel → UseCase → Repository → Network → Database → UI state), not only HTTP calls. Android and iOS, with a Compose Multiplatform inspector UI.

> Phase 0: project foundation. Only `devscope-core` and `devscope-ui-compose` exist, with placeholder content.

## Modules
| Module | Purpose |
|---|---|
| `devscope-core` | Platform-independent core (no UI, no Android/iOS APIs) |
| `devscope-ui-compose` | Compose Multiplatform inspector UI, depends on core |
| `samples/composeApp` | Shared sample UI + iOS framework |
| `samples/androidApp` | Android host |
| `samples/iosApp` | iOS host (Xcode) |

## Quick start
```
./gradlew :samples:androidApp:installDebug
```
iOS: open `samples/iosApp/iosApp.xcodeproj` in Xcode 26+ and run. See [docs/development.md](docs/development.md).

## Docs
- [Architecture](docs/architecture.md)
- [Modules](docs/modules.md)
- [Development](docs/development.md)
- [Sample walkthrough: Add to Cart](docs/sample-walkthrough.md)

Tracing engine usage: [docs/api.md](docs/api.md). Design notes: [docs/architecture.md](docs/architecture.md).
