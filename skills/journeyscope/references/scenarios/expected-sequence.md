<!-- Generated from ai/scenarios/expected-sequence.md by scripts/sync-skill-references.sh; edit there. -->
# Expected sequences

**Module:** core (`dev.chayaphon.journeyscope.core.expectation`). Use when the user wants the Inspector to flag steps a journey should show but did not.

## Edit

```kotlin
val addToCart = ExpectedSequence(
    name = "Add to Cart",
    steps = listOf(
        ExpectedStep.named("Button tap", "Add tapped"),
        ExpectedStep.category("API call", SpanCategory.NETWORK),
        ExpectedStep.named("State observed", "CartUiState"),
    ),
    appliesTo = { trace -> trace.name == "Add to Cart" },
)

JourneyScopeInspector(expectedSequences = listOf(addToCart), onClose = onClose)
```

- `ExpectedStep.named(label, namePrefix)` matches by name prefix; `ExpectedStep.category(label, category, namePrefix = "")` also constrains the category.
- Steps must appear in order. `appliesTo` limits which journeys are checked; default is all.
- Offline check: `addToCart.evaluate(trace).missing`.

## Rules

Only declared steps are ever reported missing, and the Inspector words this as "not observed (configured expectation)". Never describe a missing step as an app bug.

On Android with `devscope-android` the Inspector is auto-installed and has no parameter for sequences; embed `JourneyScopeInspector` yourself (see [android-integration](https://github.com/mandanai/devscope-compose/blob/main/docs/android-integration.md)) if expectations are required, and tell the user.

## Verify

Detail screen lists each declared step as observed or not observed.
