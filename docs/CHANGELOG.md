# Changelog

All notable changes are recorded here. The format follows [Keep a Changelog](https://keepachangelog.com/) and versions follow [Semantic Versioning](https://semver.org/) once 1.0 is reached.

## [0.2.0] - 2026-10-12

### Added
- `-noop` twins for `devscope-flow`, `-navigation`, `-network-ktor` and `-database-sqldelight` (core twin gains `TraceContext`, network config types and the `configure(config, recorder)` overload), so an app swaps real for noop by build type with identical code; `scripts/check-noop-abi.sh` guards binary compatibility. `samples/androidApp` shows the pattern.

## [0.1.0] - 2026-10-11

### Added
- Core tracing engine with coroutine-context propagation, redaction defaults and bounded in-memory recorder.
- Compose Multiplatform Inspector (`devscope-ui-compose`).
- `devscope-android`: zero-code Android setup; an ongoing notification opens the inspector, no host button, `FileProvider` or export wiring. Adds `JourneyScope.tracerFlow` to core.
- Adapters: Ktor and OkHttp network tracing, Flow/StateFlow, navigation, SQLDelight.
- Evidence-based diagnostics (`devscope-diagnostics`).
- Secure export, import and persistence (`devscope-export`), and a no-op core twin (`devscope-core-noop`).
- Phase 8: Maven publication via `com.vanniktech.maven.publish` (local-only unless credentials are supplied), Kotlin ABI baselines, Spotless + ktlint, detekt, concurrency and version-consistency tests, performance measurements, CI hardening and complete documentation.
- AI-agent knowledge base (`ai/`, `llms.txt`, `skills/journeyscope`, `scripts/install-skill.sh`) so Claude Code, Codex CLI and Gemini CLI agents can install, configure and instrument JourneyScope in a host app.

### Known limitations
See [release readiness](docs/release-readiness.md).
