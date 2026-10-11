# Changelog

All notable changes are recorded here. The format follows [Keep a Changelog](https://keepachangelog.com/) and versions follow [Semantic Versioning](https://semver.org/) once 1.0 is reached.

## [Unreleased] - 0.1.0-SNAPSHOT

### Added
- Core tracing engine with coroutine-context propagation, redaction defaults and bounded in-memory recorder.
- Compose Multiplatform Inspector (`devscope-ui-compose`).
- `devscope-android`: zero-code Android setup; an ongoing notification opens the inspector, no host button, `FileProvider` or export wiring. Adds `JourneyScope.tracerFlow` to core.
- Adapters: Ktor and OkHttp network tracing, Flow/StateFlow, navigation, SQLDelight.
- Evidence-based diagnostics (`devscope-diagnostics`).
- Secure export, import and persistence (`devscope-export`), and a no-op core twin (`devscope-core-noop`).
- Phase 8: Maven publication via `com.vanniktech.maven.publish` (local-only unless credentials are supplied), Kotlin ABI baselines, Spotless + ktlint, detekt, concurrency and version-consistency tests, performance measurements, CI hardening and complete documentation.

### Known limitations
See [release readiness](docs/release-readiness.md).
