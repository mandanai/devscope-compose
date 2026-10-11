# Verification

Run these before reporting done. Report each as pass / fail / not run (with reason).

## 1. Build

- Android: `./gradlew :<app>:assembleDebug` and `:<app>:assembleRelease` (if a release config exists).
- KMP with iOS: also `./gradlew :<shared>:compileKotlinIosSimulatorArm64`.
- Fix compile errors by correcting your use of the API, not by adding suppressions.

## 2. Release isolation

`./gradlew :<app>:dependencies --configuration releaseRuntimeClasspath` must not list `devscope-ui-compose`, `devscope-export` or `devscope-android`. `JourneyScope.configure` must appear once and be gated by a debug signal.

## 3. Journey test (preferred proof)

Add a unit test that uses the global in-memory recorder, mirroring `samples/composeApp/src/commonTest/.../AddToCartJourneyTest.kt`:

```kotlin
@Test
fun addToCartJourneyShowsEveryLayer() = runTest {
    JourneyScope.configure(JourneyScopeConfig(enabled = true))

    JourneyScope.withJourney("Add to Cart") { useCase(productId, 1) }

    val trace = JourneyScope.tracer.recorder.snapshot().single { it.name == "Add to Cart" }
    assertEquals(TraceStatus.COMPLETED, trace.status)
    val categories = trace.spans.map { it.category }.toSet()
    assertTrue(SpanCategory.USE_CASE in categories && SpanCategory.REPOSITORY in categories)
}
```

Adapt names to the project. Skip if the project has no test setup, and say so rather than adding a test framework.

## 4. On-device check (when a device or emulator is available)

Install the debug build, perform the flow, then open the Inspector (Android: tap the JourneyScope notification; on Android 13+ grant notification permission). Confirm the named journey exists with nested spans and that failures or slow steps show diagnostics. Use whatever device tooling is available (adb, an MCP Android tool, ARTEMIS); if none, mark not run.

## Done means

- Builds pass, release isolation holds, `configure` is single and debug-gated.
- At least one named journey is proven (test or device).
- Guardrails respected: no PII in names/metadata, no widened allowlists without approval.
- Final report lists modules added, files changed, flows traced, anything not traced, and unverified items.
