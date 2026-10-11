<!-- Generated from ai/scenarios/navigation.md by scripts/sync-skill-references.sh; edit there. -->
# Navigation tracing

**Module:** `devscope-navigation` (Android + iOS). Framework-neutral: nothing is observed automatically, so call it from the host's navigator.

## Edit

```kotlin
val navTracer = NavigationTracer(NavigationTraceConfig(journey = { currentJourney }))

navTracer.navigate(from = "product", to = "cart", backStackBefore = before, backStackAfter = after)
navTracer.pop(from = "cart", to = "product", backStackBefore = before, backStackAfter = after)
navTracer.markCompleted("cart")
```

Hook it where the app actually changes destination (navigator wrapper, `NavController.addOnDestinationChangedListener`, back-stack observer). Other actions: `replace`, `switchStack`, `destinationEntered`, `destinationExited`, `snapshot`.

`NavigationTraceConfig.journey` is evaluated per event when no `journey` argument is passed. Each call returns `true` only if an event was recorded; it never throws.

## Rules

- Pass route **templates** (`"product/{id}"`), never concrete values. `RouteRedactor.Default` drops query/fragment and replaces numeric/UUID-like/very long segments with `{arg}`, but do not depend on it.
- Never pass navigation arguments.
- `markCompleted` only when the host knows navigation finished (for example first frame); absence does not mean failure.
- Navigation 3: no adapter ships. Call `NavigationTracer` from your own code.

## Verify

`NAVIGATION` events with `nav.action`, `nav.from`, `nav.to` appear on the journey.
