# Guardrails

## Never

- Enable collection outside debug builds. Always `JourneyScopeConfig.debugOnly(isDebuggable = …)` from a real debug signal, never `enabled = true` literal in shared code.
- Call `JourneyScope.configure` twice, from a library module, or from a place that can run more than once per process.
- Put request/response bodies, tokens, credentials, emails, phone numbers or other PII in span names, event names or metadata. Names and labels must be stable identifiers, not interpolated data.
- Add `devscope-ui-compose` or `devscope-export` as `implementation`/`commonMain` in a module that ships to release unless the project already accepts that. Use `debugImplementation` or a debug source set.
- Combine `devscope-core-noop` with any adapter module.
- Invent APIs. Use only symbols in [api-cheatsheet.md](api-cheatsheet.md); if something seems missing, say so instead of guessing.
- Pass concrete URLs or argument values to navigation. Pass route templates.
- Wrap SQL text or bind values in database labels.
- Swallow or alter exceptions inside traced blocks. Tracing rethrows; keep it that way.

## Ask first

- Widening `headerAllowlist` or `queryParamAllowlist`, enabling `bodyCapture`, `PropagationConfig`, `includeFailureMessages`, `captureStackTraces`, or `ExportPolicy.allowBodies`.
- Changing `RedactionPolicy`, `samplingRate`, or retention.
- Turning on on-disk persistence (it writes files on the device).
- Anything that changes release-build behaviour or dependencies.

## Always

- Match the host project's style, DI, naming and dependency mechanism. Keep diffs minimal; no drive-by refactors.
- Join the ambient journey through the coroutine context. Do not thread a `Journey` parameter through layers.
- Use `launchInJourney`, `launchTraced`, or `withContext(journey.context())` for work started outside the journey's coroutine (`GlobalScope`, a plain `scope.launch`, callbacks).
- For OkHttp, tag each request made inside a journey (`tagWithCurrentJourney()`).
- Close every manually started journey: `finish()`, `fail(t)`, `cancel()` or `interrupt(reason)`. Prefer `withJourney`.
- Treat absence in the Inspector as "not instrumented", not as a bug in the app.
- Run [verification.md](verification.md) before declaring done.
