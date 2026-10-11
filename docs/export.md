# Persistence, privacy and debug export (`devscope-export`)

Everything here is opt-in. `JourneyScopeConfig.enabled = false` and `ExportPolicy.persistenceEnabled = false` are the defaults; with either off no file is created, no directory is touched and no queue exists.

## Data flow
```
finished JourneyTrace -> RedactionPipeline -> RedactedTrace -> TraceStore (disk) / TraceReportWriter (export)
```
`RedactedTrace` has an internal constructor and `TraceStore`/`TraceReportWriter` accept nothing else, so unredacted data cannot be stored or exported by accident. Redaction runs before storage and export, not only in the inspector.

## Redaction (`RedactionPipeline`)
1. **Allowlist first.** Metadata keys are kept only if `MetadataAllowlist` allows them (default: `http.*`, `db.*`, `business.status`, `nav.*`, `flow.kind`, `lifecycle.state`, `coroutine.*`, a few `state.*` counters). Everything else is dropped and counted.
2. **Sensitive keys are masked** (authorization, cookie, token, refresh, api key, otp, password, email, phone, signature, … plus `RedactionPolicy.sensitiveKeyFragments`).
3. **Bodies are dropped** (`*.body`, `*payload`) unless `ExportPolicy.allowBodies = true`.
4. **Values are scrubbed**: bearer/basic credentials, JWTs, emails, long hex/base64 secrets, phone numbers, URL user-info and secret query parameters (`token`, `sig`, `X-Amz-*`, `Expires`, …). Names and labels are scrubbed too.
5. **Failures keep only their type**; messages and stack traces are removed.

`RedactionReport` counts what was removed (never the values) and powers the review dialog.

## Persistence
```kotlin
val policy = ExportPolicy(persistenceEnabled = true, retention = 7.days, maxStoredTraces = 100, maxTotalBytes = 8 MB)
val persistence = JourneyPersistence.install(config, policy, fileSystem = { createTraceFileSystem(androidTraceDirectory(context)) })
JourneyScope.configure(config, persistence.recorder)
```
- Storage is one JSON file per trace (`<wallStart>-<id>.jtrace.json`) in a platform-protected directory: `noBackupFilesDir` on Android; Application Support, excluded from backup, `CompleteUntilFirstUserAuthentication` protection on iOS. File names never contain untrusted text (invalid ids are rejected).
- Writes are atomic (temp file + rename) and happen on a single background consumer fed by a bounded queue (`queueCapacity`, drop-oldest). Journey code never blocks on disk.
- Bounds: `retention`, `maxStoredTraces`, `maxTotalBytes`, `maxTraceBytes` (oversized traces are rejected, not truncated). Eviction removes the oldest first and runs on open and after each save.
- Failures never reach the app. They are reported to a `StorageListener`; after five consecutive write failures persistence suspends for the session (`TraceStore.resume()` re-arms it).
- **Process death:** only finished traces are written. Call `JourneyScope.interruptActive(...)` when the app is backgrounded so running journeys become `INTERRUPTED` and are saved. A hard kill loses in-flight journeys by design (no partial files); leftover `.tmp` files and corrupt or unsupported files are deleted on the next open.
- History is loaded into the inspector at start-up and merged behind live traces (live wins on id clashes).

## Report schema (`journeyscope.trace` v1)
Top level: `schema`, `schemaVersion`, `sdkVersion`, `exportedAtWallMillis`, `platform`, `appVersion?`, `config` (non-secret limits only), `traces[]`.
Trace: `id`, `name`, `status`, `platform`, `startWallMillis`, `endWallMillis?`, `durationNanos`, `droppedItems`, `failure{type}?`, `metadata`, `spans[]`, `events[]`, `findings[]?`.
Span: `id`, `parentId?`, `name`, `category`, `sequence`, `startWallMillis`, `startOffsetNanos`, `endOffsetNanos?`, `status`, `endedBySystem`, `failure?`, `metadata`.
Event: `id`, `spanId?`, `name`, `category`, `sequence`, `offsetNanos`, `wallMillis`, `metadata`.

Timing semantics: monotonic times are exported as **offsets from the trace start** (the monotonic clock means nothing across processes); wall-clock values are for display. Ids, parent links and sequence numbers are preserved, so ordering and correlation reconstruct exactly. Imported traces get `startNanos = 0`.

Versioning: `SchemaMigrator` upgrades older documents one step at a time; newer or unknown versions are rejected.

## Import (untrusted input)
`TraceImporter` validates before anything reaches the UI: size (before parsing), JSON depth/nodes/string length, schema version, field types and ranges, id and category patterns, trace/span/event counts, duplicate ids, missing parents, parent cycles, events pointing at missing spans, offsets within the trace duration. Unknown fields are ignored with a warning (`ImportLimits.strict = true` rejects them). Imported traces are redacted again, shown read-only with an "Imported" label, and never written into the live recorder. In-progress journeys are imported as `INTERRUPTED`. Nothing in an imported file is executed.

## Inspector actions
Pass `exportController = JourneyExportController(recorder, store, policy, shareSink)` to `JourneyScopeInspector`. The list offers **Select → Export**, **Import** (paste JSON) and **Clear**; the detail view offers **Export this journey**. Export always opens a review dialog (counts, size, redaction totals, included keys, optional raw contents). Nothing is sent anywhere: sharing happens only through the `ShareSink` you supply (`AndroidShareSink` chooser, `IosShareSink` share sheet) when the user confirms. Android reports over ~200 KB need a host `FileProvider`: pass `uriFor = { file -> FileProvider.getUriForFile(...) }`.

## Performance controls
`samplingRate`, `maxEventsPerTrace`, `maxEventsPerSecond`, `maxMetadataSize` (core); `retention`, `maxStoredTraces`, `maxTotalBytes`, `maxTraceBytes`, `queueCapacity`, `maxExportTraces`, `maxExportBytes` (export). The sample's `OverheadBenchmark.run()` measures caller-side cost per journey in disabled, enabled, enabled + redaction and enabled + persistence modes; run `:samples:composeApp:testAndroidHostTest --tests '*OverheadBenchmarkTest*' -i` to see numbers.
