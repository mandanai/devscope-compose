# Diagnostics

`devscope-diagnostics` — `dev.chayaphon.journeyscope.diagnostics`. Pure Kotlin on `devscope-core`; no Compose. The Inspector consumes it through `DiagnosticAnalyzer`.

```kotlin
val report = DiagnosticAnalyzer(DiagnosticConfig(expectedSequences = listOf(addToCartCompletion))).analyze(trace)
report.findings   // sorted: Error, Warning, Info
report.timing     // TimingAnalysis
```

## Contracts
`DiagnosticRule { id; evaluate(DiagnosticContext): List<DiagnosticFinding> }`, `DiagnosticFinding`, `DiagnosticSeverity {Info, Warning, Error}`, `DiagnosticCategory` (open value class), `DiagnosticContext(trace, timing, config, nowNanos)`, `DiagnosticReport`. Register custom rules with `DiagnosticAnalyzer(config, DiagnosticRules.default() + MyRule)`. A rule that throws is skipped and listed in `report.ruleFailures`. `config.disabledRules` and `config.severityOverrides` tune built-ins by rule id.

A finding separates **evidence** (what was observed), **inference** (what it might mean, only when defensible) and **next steps**. No rule names a root cause it cannot show.

## Built-in rules
| Id | Fires when |
| --- | --- |
| `journey.slow` | completed journey ≥ `slowJourney` (2 s) |
| `network.slow` | non-failed network span ≥ `slowNetworkSpan` (800 ms) |
| `database.slow` | leaf DB span ≥ `slowDatabaseOperation` (100 ms); a transaction is judged by its children |
| `network.failed` | network span `FAILED` (cancelled is not a failure) |
| `business.validation_failed` | failed `BUSINESS_VALIDATION` span, or `business.status=FAILURE` on a successful response ("despite HTTP 200") |
| `network.repeated` | ≥ 3 requests to the same method/host/path; noted as possible retries if earlier ones failed |
| `database.sequential_burst` | ≥ 5 non-overlapping DB operations under one parent |
| `journey.gap` | gap between adjacent sibling spans ≥ max(200 ms, 20 % of the journey) |
| `journey.unfinished` | status `INTERRUPTED` or spans closed by the SDK; `RUNNING` is never flagged |
| `journey.checkpoint_missing` | a completed journey lacks a step of a configured `ExpectedSequence` that `appliesTo` it |
| `timing.dominant` | one category holds ≥ `dominantShare` (50 %) of attributed time |
| `timing.database_significant` | DB holds ≥ 20 % and is not already dominant |

Each rule emits at most one finding and lists at most `maxRelatedSpans` (5) spans.

## Timing analysis
**Attribution rule:** every instant is charged to exactly one active span — the deepest, then the latest-started, then the highest sequence. Time with no active span is *untracked*. Category attributions plus untracked therefore always equal the wall-clock total, nested spans are never double-counted, and overlapping work is never summed.

- `overlaps`: pairs of spans where neither is an ancestor of the other and their intervals intersect; `isSequential = overlaps.isEmpty()`; `maxConcurrency` counts concurrent branches.
- `busy` per category is the union of its span intervals (can exceed `attributed` when deeper work owns the time).
- `slowestSpan`: closed span with the largest exclusive (own) time.
- `criticalPath` is returned only when the journey is sequential; with overlap, dependencies are ambiguous and it is `null`.
- Insight wording says "of this sequential journey" only when `isSequential`; otherwise it says percentages are of attributed wall-clock time with overlapping work present.
- Rounding: evidence uses one decimal, half-up (`940 ms / 1,265 ms` → `74.3%`); stacked-bar labels use largest-remainder rounding so whole-number percentages sum to 100.

## Not claimed
Percentages and thresholds indicate where measured time went, not why. Missing checkpoints mean "not observed by instrumentation".
