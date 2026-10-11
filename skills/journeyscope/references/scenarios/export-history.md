<!-- Generated from ai/scenarios/export-history.md by scripts/sync-skill-references.sh; edit there. -->
# Persistence, history and safe export

**Module:** `devscope-export` (Android + iOS). Opt-in. **ASK** before enabling persistence: it writes redacted trace files to device storage.

## Edit (Android, debug source set)

```kotlin
val config = JourneyScopeConfig.debugOnly(isDebuggable = isDebug)
val policy = ExportPolicy(persistenceEnabled = true)
val persistence = JourneyPersistence.install(
    config,
    policy,
    fileSystem = { createTraceFileSystem(androidTraceDirectory(context)) },
)
JourneyScope.configure(config, persistence.recorder)

val exportController = JourneyExportController(
    recorder = persistence.recorder,
    store = persistence.store,
    policy = policy,
    shareSink = AndroidShareSink(context),
)
```

- With `devscope-android`: set `JourneyScopeAndroid.exportController = exportController` before the inspector opens.
- Embedded Inspector: `JourneyScopeInspector(exportController = exportController, onClose = …)`.
- iOS: `createTraceFileSystem(directory)` and `IosShareSink` are the iOS counterparts.
- This replaces the plain `JourneyScope.configure` call from the playbook; keep one `configure` only.

## Rules

- Redaction runs before storage and again at export; do not bypass it. `ExportPolicy.allowBodies` stays `false` unless the user explicitly asks.
- Keep this wiring in debug code (`src/debug/`) with an empty counterpart in `src/release/`.
- Android reports over ~200 KB need a host `FileProvider`: `AndroidShareSink(context, uriFor = { file -> FileProvider.getUriForFile(…) })`.
- Retention defaults: 7 days, 100 traces, 8 MB total. Change only on request.

Details: [docs/export.md](https://github.com/mandanai/devscope-compose/blob/main/docs/export.md), [docs/privacy-security.md](https://github.com/mandanai/devscope-compose/blob/main/docs/privacy-security.md).

## Verify

After a journey, relaunch the app; History lists it. Export opens a review dialog before anything is shared.
