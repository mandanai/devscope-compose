# JourneyScope

Runtime debugging for Kotlin Multiplatform apps. See the whole journey of an operation, not just its HTTP calls.

JourneyScope records what happens between a tap and the resulting UI state, shows the timeline in an in-app Compose inspector, explains slow or failing steps with evidence, and exports redacted reports you can safely share.

```mermaid
flowchart LR
    UI[UI tap] --> VM[ViewModel] --> UC[UseCase] --> Repo[Repository]
    Repo --> Net[Network]
    Repo --> DB[Database]
    Net --> State[UI state]
    DB --> State
    State --> Inspector[[Inspector timeline]]
```

<div style="display: flex; gap: 12px; flex-wrap: wrap;" markdown>
![Journey list](screenshots/android-list-light.png){ width="220" }
![Journey detail](screenshots/android-detail-light.png){ width="220" }
![Diagnostic insight, dark theme](screenshots/android-detail-insight-dark.png){ width="220" }
</div>

## Pick your path

```mermaid
flowchart TD
    A([I want to...]) --> B{What are you doing?}
    B -->|Add JourneyScope to my app| C[Get started]
    B -->|Trace network, flows, navigation, database| D[Instrument]
    B -->|Read timelines and share reports| E[Inspect and share]
    B -->|Keep it out of release builds| F[Ship]
    C --> C1[getting-started.md]
    D --> D1[instrumentation.md]
    E --> E1[ui.md]
    F --> F1[release.md]
```

<div class="grid cards" markdown>

- **[Get started](getting-started.md)**: install, configure and open the Inspector in six steps.
- **[Instrument](instrumentation.md)**: Ktor, OkHttp, Flow, navigation and SQLDelight adapters.
- **[Inspect and share](ui.md)**: read a timeline, run diagnostics, export a redacted report.
- **[Ship](release.md)**: noop twins and disabled mode for release builds.

</div>

## Why JourneyScope

- **Whole journeys, not single requests.** Network, Flow, navigation and database activity are stitched into one trace through the coroutine context.
- **Private by default.** Keys, headers, URLs and failure messages are redacted at capture and again at export.
- **Zero cost in release.** Disabled until you opt in, with a `-noop` twin for release builds.
- **One inspector for both platforms.** Compose Multiplatform UI on Android and iOS.
