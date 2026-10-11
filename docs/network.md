# Network instrumentation

Both adapters feed one client-neutral engine in `devscope-core` (`dev.chayaphon.journeyscope.core.network.NetworkTracing`), so a Ktor call and an OkHttp call produce the same NETWORK span. Install only the adapter you use; neither depends on the other, and nothing in `commonMain` or the UI module depends on OkHttp.

## Ktor (Android + iOS)
```kotlin
val client = HttpClient(engine) {
    install(HttpRequestRetry) { /* ... */ }        // install retry/redirect first: each attempt gets its own span
    install(JourneyScopeNetwork) {
        trace = NetworkTraceConfig(headerAllowlist = setOf("x-request-id"))
    }
}
```
The journey is read from the calling coroutine's `TraceContext`, so calls made inside `JourneyScope.withJourney { }`, `journey.traceSuspend { }` or `launchInJourney` are correlated and parented automatically.

## OkHttp (Android only)
```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(JourneyScopeInterceptor(NetworkTraceConfig()))
    .build()

val request = Request.Builder().url(url).tagWithCurrentJourney().build()   // inside a coroutine
val request = Request.Builder().url(url).journeyContext(journey.context()[TraceContext]).build()   // explicit
```
OkHttp runs interceptors on its own threads, so the journey travels as a request tag. Retrofit users can attach the tag in a call-site helper. Use `addNetworkInterceptor` instead for one span per redirect/retry hop.

## What is recorded
Span name `METHOD host/path`, category `NETWORK`, status OK for 1xx-3xx, FAILED for 4xx/5xx and transport errors, CANCELLED on cancellation. Metadata: `http.method`, `http.host`, `http.port`, `http.path`, `http.status`, `http.failure` (`TIMEOUT`, `CONNECTION`, `DNS`, `TLS`, `CANCELLED`, `HTTP_CLIENT_ERROR`, `HTTP_SERVER_ERROR`, `PROTOCOL`, `UNKNOWN`), `http.protocol`, `http.streaming`, byte sizes when `Content-Length` is known, allowlisted headers, `business.status` and `returnCode` when a validator ran.

## Requests outside a journey
`NetworkTraceConfig.noJourneyPolicy`:

| Policy | Behaviour |
| --- | --- |
| `Ignore` (default) | Nothing is recorded. |
| `Standalone` | Each request becomes its own single-span trace. |
| `Explicit(journey)` | Each request joins the supplied journey as a root span. |

A request is never attached to "the most recent" journey. A request whose journey is disabled or sampled out records nothing.

## Sensitive data
Nothing sensitive is recorded by default. Header values are recorded only for names in `headerAllowlist`, and `Authorization`, `Cookie`, `Set-Cookie`, `Proxy-Authorization`, API-key and token headers stay denied even when listed. Query values are recorded only for `queryParamAllowlist` names; user info and fragments are dropped; URLs carrying signing parameters (`X-Amz-Signature`, `X-Amz-Credential`, `Signature`, ...) drop the whole query and set `http.url.signed=true`; long opaque path segments (UUIDs, hex ids, tokens) become `{id}`. Request and response bodies are never stored.

## Business validation
Register a `BusinessResponseValidator` to classify application-level outcomes separately from HTTP status:
```kotlin
NetworkTraceConfig(
    bodyCapture = BodyCapture(maxBytes = 2048),
    businessValidator = { _, response ->
        when (val code = response.bodySnippet?.let { parseReturnCode(it) }) {   // your own parser
            "0000" -> BusinessResult(BusinessStatus.SUCCESS, code)
            "1001" -> BusinessResult(BusinessStatus.SESSION_EXPIRED, code)
            else -> null
        }
    },
)
```
A throwing validator is ignored. The HTTP-derived span status is never changed by the business result.

## Outbound trace headers
Off by default. `PropagationConfig(trustedHosts = setOf("api.example.com", "*.internal.example.com"))` adds `X-JourneyScope-Trace-Id` and `X-JourneyScope-Span-Id` to requests whose host matches exactly (or by `*.suffix`) and, unless `requireHttps = false`, only over HTTPS. Redirects are evaluated per hop only when the adapter sees them as separate requests (OkHttp network interceptor, Ktor with redirect installed before the plugin).

## Limitations
- **Ktor parents come only from the coroutine context.** Work started in `GlobalScope` or a plain `scope.launch` loses the journey; use `launchInJourney` or `withContext(journey.context())`.
- **OkHttp has no coroutine context.** Untagged requests follow `noJourneyPolicy`.
- **Duration is time to response headers**, not body download. Streaming bodies are left untouched and flagged `http.streaming`.
- **Sizes** are recorded only when `Content-Length` is known.
- **Body capture** is only for the business validator, capped at 4 KiB, limited to allowlisted content types with a known length, and forces buffering in Ktor (`HttpClientCall.save()`); OkHttp uses `peekBody`, which does not consume. Both skip streaming responses.
- Retries and redirects produce one span per attempt only when they happen inside the plugin/interceptor position described above.
