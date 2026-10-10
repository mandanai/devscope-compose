# Navigation tracing (`devscope-navigation`)

A framework-neutral recorder. The host calls it from its navigation controller or navigation state owner; nothing is observed automatically.

```kotlin
val tracer = NavigationTracer(NavigationTraceConfig(journey = { currentJourney }))
tracer.navigate(from = "product", to = "cart", backStackBefore = before, backStackAfter = after)
tracer.markCompleted("cart")      // only when the host decides navigation finished (e.g. first frame)
```

Actions: `navigate`, `pop`, `replace`, `switchStack`, `destinationEntered`, `destinationExited`, `snapshot`, `markCompleted`. Each becomes a `NAVIGATION` event with `nav.action`, `nav.from`, `nav.to`, `nav.stack.before`, `nav.stack.after`, `nav.trace`.

## Data safety
Pass route **templates**. `RouteRedactor.Default` drops query and fragment and replaces numeric, UUID-like, `@`/`=`-containing or very long path segments with `{arg}`. Supply `routeRedactor` for custom rules. Navigation arguments are never accepted. Stacks are bounded by `maxStackDepth`.

## Automatically observed vs manual
Nothing is observed automatically. A completion checkpoint exists only if the host calls `markCompleted`; its absence does not mean navigation failed.

## Navigation 3
No adapter ships: the version catalog has no Navigation 3 dependency and its target support is unverified. The shared model is platform independent, so an adapter can later live in its own module that calls `NavigationTracer`.

## Expected sequences
`ExpectedSequence` (in core) lets an app declare steps such as button click → API → state observed → navigation. Pass it to `JourneyScopeInspector(expectedSequences = …)`; the detail screen lists each step as observed or "not observed (configured expectation)". Only configured steps are ever listed, and absence is never presented as broken behavior.
