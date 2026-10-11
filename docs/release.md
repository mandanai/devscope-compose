# Release safety and disabled mode

## Disabled by default
`JourneyScope` ships disabled. While `enabled = false` every journey is an inert singleton, no recorder entry is created, `JourneyPersistence.install` returns a plain in-memory recorder without invoking your file-system factory, and the export controller has nothing to persist. Use `JourneyScopeConfig.debugOnly(isDebuggable)` so release builds stay off.

## Keep the inspector and export out of release
Only host-side instrumentation needs to remain in release; the UI and export modules are debug tools.

Android (`app/build.gradle.kts`):
```kotlin
dependencies {
    debugImplementation(project(":devscope-ui-compose"))   // inspector UI + export
    debugImplementation(project(":devscope-export"))
    implementation(project(":devscope-core"))               // or releaseImplementation(devscope-core-noop), below
}
```
Create the inspector, `JourneyPersistence` and `JourneyExportController` from `src/debug/` code (for example a `DebugTools` object exposing a composable) and provide an empty counterpart in `src/release/`.

iOS: build the Kotlin framework in two variants. The release framework exports only the app code (and core or core-noop); the debug framework also links the UI and export modules. Reference the inspector entry point from Swift under `#if DEBUG`.

## `devscope-core-noop`
A release twin of the host-facing API: `JourneyScope`, `JourneyTracer`, `Journey`, `SpanHandle`, `JourneyScopeConfig`, `RedactionPolicy`, `DiagnosticMetadata`, `SpanCategory`, `SpanStatus`, ids. It never enables and every call returns immediately. The config and model files are compiled from the real core sources, so they cannot drift; the facade classes are hand-written.

```kotlin
debugImplementation(project(":devscope-core"))
releaseImplementation(project(":devscope-core-noop"))
```
Duplicate classes are prevented structurally: the noop artifact publishes the same Gradle capability as `devscope-core`, so a classpath containing both fails resolution ("Cannot select module with conflict on capability").

Limits to know about:
- The noop covers the core facade only. Adapters (`devscope-flow`, `-navigation`, `-network-*`, `-database-sqldelight`) depend on the real core, so a release build using the noop must also exclude them (keep their call sites in debug source sets). If your main sources call adapter APIs, keep the real core in release with `enabled = false`; its disabled path is a single flag check.
- `JourneyScope.configure(config)` in the noop takes no recorder, because the recorder types do not exist there; recorder and persistence wiring belongs in debug code.
