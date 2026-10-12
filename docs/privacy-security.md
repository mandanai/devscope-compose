# Privacy and security

## Defaults
- **Off by default.** Nothing is collected until the host calls `JourneyScope.configure(enabled = true)`. `JourneyScopeConfig.debugOnly(isDebuggable)` ties it to the build type.
- **Local only.** The SDK has no network transport. Traces stay in memory, optionally on-device storage, and leave the device only when a user shares an export.
- **Bounded.** `maxTraces` (50), `maxEventsPerTrace` (500), `maxMetadataSize` (1024) and an optional `maxEventsPerSecond` cap memory.

## Redaction
1. **Capture time.** `RedactionPolicy` redacts values whose key contains `password`, `secret`, `token`, `authorization`, `auth`, `cookie`, `session`, `apikey`, `credential`, `ssn`, `card`, `cvv`, `pin` and similar. Failures keep only the exception class by default.
2. **Network.** Sensitive headers always redacted; unlisted headers dropped; query values redacted; path ids normalized; bodies not stored.
3. **Export.** `RedactionPipeline` runs again over every trace, applies a metadata allow-list and scrubs values. The export is previewed on screen before sharing.

Redaction is key- and pattern-based. It cannot recognize an arbitrary secret placed in a free-text value, so do not put personal data in span names or metadata values.

## Import hardening
Imported reports are validated for schema version, size and structure; imported traces are flagged as imported and never mixed with live data silently.

## Release builds
Keep the Inspector and export modules out of release (`debugImplementation`), or leave the SDK disabled. See [release](release.md).

## Reporting a vulnerability
Report privately through a GitHub security advisory; see [SECURITY.md](https://github.com/mandanai/devscope-compose/blob/main/SECURITY.md).
