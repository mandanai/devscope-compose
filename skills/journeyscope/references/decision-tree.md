<!-- Generated from ai/decision-tree.md by scripts/sync-skill-references.sh; edit there. -->
# Decision tree

Search the host project for these signals and pick modules and scenarios. Add only what the project actually uses.

## Platform

| Signal | Do |
|---|---|
| Android app, no KMP | `devscope-core` + `debugImplementation(devscope-android)`. No Inspector code needed. |
| KMP with iOS target | `devscope-core` in `commonMain`; embed `JourneyScopeInspector` (`devscope-ui-compose`) and, for history/sharing, `devscope-export`. Do **not** add `devscope-android` to iOS. |
| KMP with Android + iOS | Android app module as Android-only above; shared module as KMP. |
| Android multi-module | `devscope-core` in the app module and every module that opens journeys (or `api` from a shared `:core`); `configure` only in the app module. |

## Adapters

| Signal in code | Module | Scenario |
|---|---|---|
| `io.ktor.client` / `HttpClient(` | `devscope-network-ktor` | [network-ktor](scenarios/network-ktor.md) |
| `okhttp3` / Retrofit | `devscope-network-okhttp` (Android only) | [network-okhttp](scenarios/network-okhttp.md) |
| `StateFlow` / `SharedFlow` / `viewModelScope.launch` the user wants observed | `devscope-flow` | [flow-state](scenarios/flow-state.md) |
| Any navigator (Compose Navigation, Voyager, Decompose, custom) | `devscope-navigation` | [navigation](scenarios/navigation.md) |
| `app.cash.sqldelight` | `devscope-database-sqldelight` | [database-sqldelight](scenarios/database-sqldelight.md) |
| Room, Realm, other DB | none ships; use manual spans with `SpanCategory.DATABASE` | [trace-usecase-flow](scenarios/trace-usecase-flow.md) |
| User wants "did these steps happen" checks | core `ExpectedSequence` | [expected-sequence](scenarios/expected-sequence.md) |
| User wants history, import or sharing reports | `devscope-export` | [export-history](scenarios/export-history.md) |

## Always

- [trace-usecase-flow](scenarios/trace-usecase-flow.md) for the named flow.
- [release-safety](scenarios/release-safety.md) for every install.

## Do not add

| Case | Reason |
|---|---|
| `devscope-network-okhttp` or `devscope-android` on iOS | Android-only. |
| `devscope-core-noop` together with any adapter | Capability conflict by design; adapters need the real core. Keep real core with `enabled = false` instead. |
| Navigation 3 adapter | None ships. Call `NavigationTracer` from the host navigator. |
| Auto SQL capture | Not supported; wrap operations explicitly. |

## Ambiguity

If the user names a flow but several entry points could start it, pick the user-action handler (click/intent), state your choice, and continue. **ASK** only when the choice would change more than one file's semantics (for example two competing view models for the same screen).
