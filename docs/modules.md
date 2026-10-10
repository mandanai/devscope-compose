# Modules

## Implemented
- `devscope-core` — `dev.journeyscope.core`; `JourneyScope.isEnabled`, `JourneyScope.VERSION`.
- `devscope-ui-compose` — `dev.journeyscope.ui`; `JourneyScopeInspector` journey list/detail UI (see [ui.md](ui.md)).
- `samples/composeApp` — layered Add to Cart sample instrumented end to end (see [sample-walkthrough.md](sample-walkthrough.md)).

## Planned (not yet created)
`devscope-network-ktor`, `devscope-network-okhttp`, `devscope-flow`, `devscope-navigation`, `devscope-database-sqldelight`, `devscope-diagnostics`, `devscope-export`.

## Adding a module
1. Create the directory with `build.gradle.kts` applying `journeyscope.kmp.library` (and `journeyscope.compose` / `journeyscope.publishing` as needed).
2. Add `include(":module-name")` to `settings.gradle.kts`.
3. Depend on `projects.devscopeCore` only; never on another adapter.
4. Put code under `src/commonMain/kotlin/dev/journeyscope/<area>`; the Android namespace is derived automatically.
5. Add a `commonTest` test.
