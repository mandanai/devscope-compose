# Modules

## Implemented
- `devscope-core` — `dev.journeyscope.core`; `JourneyScope.isEnabled`, `JourneyScope.VERSION`; shared client-neutral network engine in `dev.journeyscope.core.network`.
- `devscope-ui-compose` — `dev.journeyscope.ui`; `JourneyScopeInspector` journey list/detail UI (see [ui.md](ui.md)).
- `devscope-flow` — `dev.journeyscope.flow`; `traceFlow`, `traceState`, `traceShared`, `observeWithJourneyScope`, `launchTraced`/`asyncTraced`; Android lifecycle adapter in `androidMain` only (see [flow.md](flow.md)).
- `devscope-navigation` — `dev.journeyscope.navigation`; framework-neutral `NavigationTracer` (see [navigation.md](navigation.md)).
- `devscope-network-ktor` — `dev.journeyscope.network.ktor`; `JourneyScopeNetwork` Ktor client plugin, Android + iOS (see [network.md](network.md)).
- `devscope-network-okhttp` — `dev.journeyscope.network.okhttp`; `JourneyScopeInterceptor`, Android only (see [network.md](network.md)).
- `devscope-database-sqldelight` — `dev.journeyscope.database.sqldelight`; `DatabaseTracing` explicit query/insert/update/delete/transaction spans for SQLDelight (see [database.md](database.md)).
- `devscope-diagnostics` — `dev.journeyscope.diagnostics`; `DiagnosticAnalyzer`, rule contracts, built-in rules and `TimingAnalysis` (see [diagnostics.md](diagnostics.md)).
- `samples/composeApp` — layered Add to Cart sample instrumented end to end (see [sample-walkthrough.md](sample-walkthrough.md)).

## Planned (not yet created)
`devscope-export`.

## Adding a module
1. Create the directory with `build.gradle.kts` applying `journeyscope.kmp.library` (and `journeyscope.compose` / `journeyscope.publishing` as needed).
2. Add `include(":module-name")` to `settings.gradle.kts`.
3. Depend on `projects.devscopeCore` only; never on another adapter.
4. Put code under `src/commonMain/kotlin/dev/journeyscope/<area>`; the Android namespace is derived automatically.
5. Add a `commonTest` test.
