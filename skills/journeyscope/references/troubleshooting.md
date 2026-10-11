<!-- Generated from ai/troubleshooting.md by scripts/sync-skill-references.sh; edit there. -->
# Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Dependency not found | Pre-release; not published | `./gradlew publishToMavenLocal` in the JourneyScope repo; add `mavenLocal()` first in repositories |
| Capability conflict (`devscope-core` vs `-noop`) | Both on a classpath | Noop only with no adapters; otherwise keep real core, `enabled = false` in release |
| No notification / Inspector unreachable (Android) | `devscope-android` missing, not debuggable, or POST_NOTIFICATIONS denied (Android 13+) | Add `debugImplementation(devscope-android)`, confirm `configure` ran with `debugOnly(true)`, grant permission, or `JourneyScopeAndroid.launch(context)` |
| Nothing recorded | SDK disabled, sampled out, or `configure` not called | Check `JourneyScope.isEnabled`, `samplingRate`, single `configure` in the app module |
| Journey exists but child spans are missing | Work left the coroutine context (`GlobalScope`, bare `launch`, callback) | `launchInJourney`, `launchTraced`, or `withContext(journey.context())` |
| Ktor requests not traced | Plugin installed before retry/redirect, or call outside journey | Install after retry plugins; start journey around the call |
| OkHttp requests not traced | Request not tagged | `tagWithCurrentJourney()` (or `@Tag TraceContext` in Retrofit) |
| Network span has no query or headers | Redaction by default | Intended; allowlists need user approval |
| Flow events missing | No ambient journey in the UI collector | Pass `FlowTraceOptions(journey = …)` provider |
| `StateFlow` shows fewer values than emitted | Conflation upstream of the wrapper | Expected; only delivered values are reported |
| Navigation events missing | Nothing is automatic | Call `NavigationTracer` from the navigator |
| DB spans missing | SQL not wrapped | Wrap with `DatabaseTracing`; run inside `withContext(io)` |
| Journey stuck `RUNNING` | Manual `startJourney` never finished | Use `withJourney`, or `finish/fail/cancel/interrupt`; call `interruptActive` when app backgrounds |
| iOS build can't find the framework | Xcode build phase missing | Run `./gradlew :<module>:embedAndSignAppleFrameworkForXcode` as in `samples/iosApp` |

Still failing: read the matching human doc in [docs/](https://github.com/mandanai/devscope-compose/blob/main/docs/getting-started.md) and report the exact error to the user instead of guessing.
