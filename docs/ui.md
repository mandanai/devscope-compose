# Inspector UI (`devscope-ui-compose`)

One shared Compose Multiplatform UI for Android and iOS. Entry point:

```kotlin
JourneyScopeInspector(
    source = JourneyScope.tracer.recorder.asTraceSource(), // default
    thresholds = InspectorThresholds.from(JourneyScope.tracer.config), // default
    onClose = { /* host decides */ },
)
```

## Structure

| Package | Role |
| --- | --- |
| `state` | `JourneyTraceSource` (flow of `TraceBatch`), recorder adapter (`asTraceSource`, ticks while a journey is running), `InspectorPresenter` (maps on `Dispatchers.Default`), `InspectorState` (destination, filters, expansion, scroll target) |
| `mapping` | `TraceMapper` (domain → presentation), `aggregateByCategory`, `JourneyInsightProvider`, `MetadataDisplayPolicy`, `InspectorThresholds` |
| `model` | Immutable presentation models, `JourneyCategory`, formatting |
| `components` | `JourneyHeader`, `JourneyStatusBadge`, `JourneyDurationSummary`, `JourneyDurationBreakdown`, `journeyTimeline`/`JourneyTimelineItem`, `JourneyCategoryFilterRow`, `JourneyDiagnosticSection`, `JourneyEmptyState`/`ErrorState`/`LoadingState` |
| `screen` | `JourneyListScreen`, `JourneyDetailScreen`, adaptive `JourneyInspectorScreen` (two panes at ≥ 600 dp) |
| `theme` | `JourneyScopeTheme`, `JourneyColors` |
| `demo` | `DemoJourneys` deterministic fixtures (kept out of components) |

Navigation is a saved state machine (`List` / `Detail(traceId)`); back uses `NavigationBackHandler` (multiplatform).

## Duration aggregation policy

Implemented in `devscope-diagnostics` (`analyzeTiming`); sweep-line over the trace: every instant is charged to one category — the deepest active span, then the latest-started, then the highest sequence. Concurrent spans therefore never double-count. Time with no active span is "Untracked". Segments always sum to the trace duration; open spans are measured to "now".

Category buckets: UI_INTERACTION, VIEW_MODEL, USE_CASE, REPOSITORY, BUSINESS_VALIDATION, STATE_EMISSION, STATE, LIFECYCLE, NAVIGATION → **UI / VM**; NETWORK → **Network**; DATABASE → **Storage**; UI_RENDER → **Render**; anything else → **Other**.

The demo "Add to Cart" journey models eight sequential, non-overlapping stages: 65 + 940 + 150 + 110 = 1,265 ms (74 % network).

## Status and diagnostics

"Slow Flow" = completed journey with duration ≥ `InspectorThresholds.slowJourney` (default: `JourneyScopeConfig.slowJourneyThreshold`). A step is highlighted when its own time ≥ `slowSpan` (500 ms). Findings come from a `DiagnosticAnalyzer` (see [diagnostics.md](diagnostics.md)); pass `analyzer =` to `JourneyScopeInspector` to change thresholds or add rules (default: built-in rules, `slowJourney` from `InspectorThresholds`, plus any `expectedSequences`). The detail screen ends with a **Timing breakdown** (attributed time and one-decimal percent per category, overlap note, slowest step, critical path when sequential) and **Diagnostics** cards showing severity, evidence, inference, suggested investigation, metrics and "Show …" buttons that reveal related steps. Wording is hedged — share of time alone does not prove a cause.

## Metadata safety

`MetadataDisplayPolicy.Default` renders only allowlisted keys (`detail`, `journey.source`, `db.operation`, `db.label`, `db.transaction.*`, `db.rows`, `http.*`-style keys you add, `returnCode`, `component`, `state.*`, `nav.*`, `flow.kind`, `collector.id`, `lifecycle.state`, `coroutine.*`, `ui.checkpoint`); other keys show as "Hidden by display policy". Failure messages are hidden unless `showFailureMessages = true`. `MetadataDisplayPolicy.ShowAll` is an explicit opt-in for debug tooling.

## Theming

`JourneyScopeTheme(colors, typography)`; `JourneyColors.light()` / `dark()` are data classes — `copy()` any token (category colors, bottleneck, status). Typography defaults to the enclosing `MaterialTheme`. Pass `colors =` to `JourneyScopeInspector` to apply a custom palette.

## Accessibility

Rows, list cards, status and the duration bar expose spoken descriptions ("940 milliseconds", percentages); rows announce expanded/collapsed state; icons are drawn with `Canvas` and are decorative.
