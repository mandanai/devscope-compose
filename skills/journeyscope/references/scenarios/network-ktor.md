<!-- Generated from ai/scenarios/network-ktor.md by scripts/sync-skill-references.sh; edit there. -->
# Ktor network tracing

**Module:** `devscope-network-ktor` (Android + iOS). Add to the module that builds the `HttpClient`.

## Edit

```kotlin
val client = HttpClient(engine) {
    install(HttpRequestRetry) { /* existing */ }   // keep retry/redirect first: one span per attempt
    install(JourneyScopeNetwork) {
        trace = NetworkTraceConfig()
    }
}
```

Imports: `dev.chayaphon.journeyscope.network.ktor.JourneyScopeNetwork`, `dev.chayaphon.journeyscope.core.network.NetworkTraceConfig`.

Install the plugin **after** any retry/redirect plugin, and after other plugins whose latency should be excluded from the span.

## Defaults you should keep

Bodies are never stored. Only allow-listed headers/query values are recorded; `Authorization`, `Cookie`, `Set-Cookie`, API-key and token headers stay redacted even if listed. Opaque path segments become `{id}`.

## Optional (ASK first)

- `NetworkTraceConfig(headerAllowlist = setOf("x-request-id"))` for correlation headers.
- `bodyCapture = BodyCapture(...)` + `businessValidator` to classify HTTP 200 business failures (enables `business.status`). Reads a bounded snippet and forces buffering in Ktor.
- `PropagationConfig(trustedHosts = …)` to send `X-JourneyScope-Trace-Id`/`Span-Id` to your own hosts over HTTPS.
- `noJourneyPolicy`: `Ignore` (default), `Standalone`, `Explicit(journey)` for requests outside a journey.

## Gotchas

- The parent comes only from the calling coroutine's `TraceContext`. Requests launched from `GlobalScope` or a plain `scope.launch` lose the journey; use `launchInJourney`/`withContext(journey.context())`.
- Duration is time to response headers, not body download.
- Multiple clients: install the plugin on each one the user wants traced.

## Verify

Call an endpoint inside a journey; a `NETWORK` span named `METHOD host/path` with `http.status` appears under the repository span.
