# Modules

## Implemented
- `devscope-core` — `dev.chayaphon.journeyscope.core`; `JourneyScope.isEnabled`, `JourneyScope.VERSION`; shared client-neutral network engine in `dev.chayaphon.journeyscope.core.network`.
- `devscope-ui-compose` — `dev.chayaphon.journeyscope.ui`; `JourneyScopeInspector` journey list/detail UI (see [ui.md](ui.md)).
- `devscope-android` — `dev.chayaphon.journeyscope.android`; Android only. Adding it is the whole setup: an `androidx.startup` initializer posts an ongoing notification that opens the inspector, with a bundled `FileProvider` for sharing (see [android-integration.md](android-integration.md)).
- `devscope-flow` — `dev.chayaphon.journeyscope.flow`; `traceFlow`, `traceState`, `traceShared`, `observeWithJourneyScope`, `launchTraced`/`asyncTraced`; Android lifecycle adapter in `androidMain` only (see [flow.md](flow.md)).
- `devscope-navigation` — `dev.chayaphon.journeyscope.navigation`; framework-neutral `NavigationTracer` (see [navigation.md](navigation.md)).
- `devscope-network-ktor` — `dev.chayaphon.journeyscope.network.ktor`; `JourneyScopeNetwork` Ktor client plugin, Android + iOS (see [network.md](network.md)).
- `devscope-network-okhttp` — `dev.chayaphon.journeyscope.network.okhttp`; `JourneyScopeInterceptor`, Android only (see [network.md](network.md)).
- `devscope-database-sqldelight` — `dev.chayaphon.journeyscope.database.sqldelight`; `DatabaseTracing` explicit query/insert/update/delete/transaction spans for SQLDelight (see [database.md](database.md)).
- `devscope-diagnostics` — `dev.chayaphon.journeyscope.diagnostics`; `DiagnosticAnalyzer`, rule contracts, built-in rules and `TimingAnalysis` (see [diagnostics.md](diagnostics.md)).
- `devscope-export` — `dev.chayaphon.journeyscope.export`; opt-in persistence (`JourneyPersistence`, `TraceStore`), centralized `RedactionPipeline`, versioned JSON report, strict `TraceImporter`, `JourneyExportController`, share sinks (see [export.md](export.md)).
- `devscope-core-noop`, `devscope-flow-noop`, `devscope-navigation-noop`, `devscope-network-ktor-noop`, `devscope-database-sqldelight-noop` — release-build twins of the matching module; same Gradle capability so both can never share a classpath (see [release.md](release.md)).
- `samples/composeApp` — layered Add to Cart sample instrumented end to end (see [sample-walkthrough.md](sample-walkthrough.md)).


## Adding a module
1. Create the directory with `build.gradle.kts` applying `journeyscope.kmp.library` (and `journeyscope.compose` / `journeyscope.publishing` as needed).
2. Add `include(":module-name")` to `settings.gradle.kts`.
3. Depend on `projects.devscopeCore` only; never on another adapter.
4. Put code under `src/commonMain/kotlin/dev/chayaphon/journeyscope/<area>`; the Android namespace is derived automatically.
5. Add a `commonTest` test.
