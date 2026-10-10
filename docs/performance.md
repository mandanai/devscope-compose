# Performance

These numbers are **measurements on one machine**, not guarantees. They show cost orders of magnitude and where cost grows; they are not a claim of zero overhead. Re-run them on your own target devices before relying on them.

## Environment
| | |
|---|---|
| Machine | Apple M3 Pro, 12 CPUs, 36 GB RAM, macOS (aarch64) |
| JVM | OpenJDK 17.0.20.1 (host JVM unit-test runner, not an Android device) |
| Kotlin / Gradle | 2.4.21 / 9.6.1 |
| Method | `PerformanceMeasurementTest` (`samples/composeApp`, androidHostTest): 5 warm-up runs, then 20 timed runs, 20,000 operations per run, `TimeSource.Monotonic`, median / p95 |
| Not measured | Android devices, ART, iOS/Kotlin-Native, frame time, battery. Host JVM results will differ from those. |

Reproduce:
```bash
./gradlew :samples:composeApp:testAndroidHostTest --tests '*PerformanceMeasurementTest*' --rerun-tasks
cat samples/composeApp/build/performance-report.txt
./gradlew :devscope-ui-compose:testAndroidHostTest --tests '*InspectorScalingMeasurementTest*' --rerun-tasks
cat devscope-ui-compose/build/inspector-mapping-report.txt
```

## Results (median / p95)
| Measurement | Result |
|---|---|
| `JourneyScope.configure` (initialization) | 260 ns / 375 ns per call |
| Enabled `recordEvent`, 400 ops per trace | 290 ns / 391 ns |
| Enabled span `trace {}` (empty body), 400 ops per trace | 1.47 µs / 1.82 µs |
| Enabled `recordEvent`, 4,000 ops per trace | 770 ns / 804 ns |
| Enabled span `trace {}`, 4,000 ops per trace | 27.7 µs / 29.7 µs |
| **Disabled** `recordEvent` | 16 ns / 18 ns |
| **Disabled** span `trace {}` | 2 ns / 17 ns |
| Memory at default limits (50 traces x 500 events, one metadata entry each) | about 10.9 MiB retained heap (single measurement after `System.gc()`; indicative only) |
| Export: redact and serialize 50 traces / 25,000 events (about 3.7 million characters of JSON) | 25 ms / 34 ms |
| Inspector mapping (`TraceMapper.detail`), 500 spans+events | 2.7 ms |
| Inspector mapping, 2,000 spans+events | 17 ms |
| Inspector mapping, 10,000 spans+events | 353 ms |

Mapping time is the presentation-model build, not Compose rendering or frame time; the lists are lazy.

## Reading the numbers
- Disabled mode is a flag check plus a shared inert object; the nanosecond figures are close to the measurement floor.
- **Per-operation cost grows with trace size.** The recorder keeps immutable snapshots, so span recording at 4,000 spans per trace is roughly 19x the cost at 400, and Inspector mapping is super-linear (about 130x from 500 to 10,000 rows). The default `maxEventsPerTrace = 500` keeps both small. Raising the cap into the thousands is not recommended without measuring on your target.
- Persistence is asynchronous and excluded from the caller-side numbers. `OverheadBenchmark` in the sample compares disabled, enabled, redaction and persistence modes per journey.
- Memory is bounded by `maxTraces x maxEventsPerTrace`; it is not proportional to total traffic.

## Not yet measured
Android device and iOS timings, steady-state GC impact, and Inspector frame rate with very large histories. These are tracked in the release readiness report as next steps.
