# OkHttp / Retrofit network tracing

**Module:** `devscope-network-okhttp` (**Android only**). Add where the `OkHttpClient` is built.

## Edit

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(JourneyScopeInterceptor())
    .build()
```

Imports: `dev.chayaphon.journeyscope.network.okhttp.JourneyScopeInterceptor`.

Use `addNetworkInterceptor` instead if one span per redirect/retry hop is wanted.

## Tag requests (required for correlation)

OkHttp runs interceptors on its own threads, so the journey travels as a request tag:

```kotlin
val request = Request.Builder().url(url).tagWithCurrentJourney().build()   // inside a coroutine
```

Imports: `…okhttp.tagWithCurrentJourney` (suspend; call inside a coroutine). Explicit form: `.journeyContext(journey.context()[TraceContext])`.

### Retrofit

Retrofit builds the `Request` for you. The adapter reads the tag keyed by `TraceContext::class.java`, which is what Retrofit's `@Tag` parameter (Retrofit 2.10+) sets from the parameter's type:

```kotlin
interface CartApi {
    @POST("cart") suspend fun add(@Body body: AddRequest, @Tag trace: TraceContext?): AddResponse
}
// call site, inside a coroutine
api.add(body, currentTraceContext())
```

Check the project's Retrofit version first. If it is older than 2.10, or changing every service signature is too invasive, say so: untagged requests follow `NetworkTraceConfig.noJourneyPolicy` (default `Ignore`, so they are simply not recorded). Offer `Standalone` as a stop-gap and **ASK**.

## Gotchas

- No coroutine context on OkHttp threads: an untagged request never attaches to "the most recent" journey.
- Defaults to redaction; see [network-ktor](network-ktor.md) for allowlist/validator options (same `NetworkTraceConfig`).
- Not available on iOS; a KMP project using OkHttp on Android and Ktor on iOS installs each adapter in its own source set.

## Verify

A tagged call inside a journey produces a `NETWORK` span under the active span.
