# Release readiness report

Version `0.2.0`. Nothing has been published to any remote repository.

## Verdict
**Not ready for a public release.** The SDK builds, tests, lints, publishes locally, is consumed from Maven Local on Android and iOS compilation, and runs on an Android emulator. iOS tests, the Xcode build and an iOS run were not executed in this environment, and repository metadata is still placeholder.

## Verified (commands actually run on the host)
| Check | Result |
|---|---|
| `spotlessCheck detekt checkKotlinAbi` | pass |
| `testAndroidHostTest` (all modules, `--rerun-tasks`) | pass: 251 tests, 0 failed, 0 skipped |
| `:samples:androidApp:assembleDebug` | pass |
| `compileCommonMainKotlinMetadata` | pass |
| `compileKotlinIosSimulatorArm64`, `compileKotlinIosArm64` (all modules incl. sample) | pass |
| `publishToMavenLocal` | pass; POM/module metadata inspected for `devscope-core` |
| Throwaway consumer project (outside the repo) using only `mavenLocal()`: core, flow, navigation, diagnostics, export, ktor, ui-compose (+ okhttp on Android), compiling the documented snippets | `compileAndroidMain`, `compileKotlinIosSimulatorArm64`, `compileKotlinIosArm64` pass |
| Android emulator (`emulator-5554`, API 16 KB-page image): install debug APK, tap Add to Cart, open Inspector and journey detail, open export review (cancelled, nothing shared) | works: journey `Completed` 279 ms with UI/VM, Network, Storage, Render breakdown and nested ViewModel to UseCase spans; export preview reported 1 journey, 10 spans, 5 events, 21 fields masked, bodies excluded; no error-level logcat for the app |
| `./gradlew help --warning-mode all` | one deprecation ("Project object as a dependency notation"), raised inside AGP 9.1.1's KMP plugin (`VariantDependencies.createForKotlinMultiplatform`), i.e. upstream; none from this repo's scripts |
| Add to Cart trace correctness on host | `AddToCartJourneyTest` (18 tests) passes |
| Performance measurements | recorded in [performance.md](performance.md) |

Host test counts: core 64, ui-compose 33, diagnostics 28, export 42, flow 17, navigation 9, sqldelight 11, network-ktor 11, network-okhttp 11, core-noop 1, sample 24.

## Not verified
| Item | Why |
|---|---|
| `iosSimulatorArm64Test` (Kotlin/Native tests) | Full Xcode is not installed (`xcode-select` points at Command Line Tools; `simctl` is missing). A run started and had to be stopped. Configured in CI on `macos-latest`. |
| Linking the sample framework, `xcodebuild` of `samples/iosApp`, running on an iOS simulator or device | Same reason. The shared Inspector therefore has been seen working on Android only. |
| Whether the CI workflows pass on GitHub | Written, never executed. Known risks: `checkKotlinAbi` is now set to run on macOS because it includes iOS klib dumps (only ever run locally on macOS); `xcodebuild` uses `-target iosApp` because the project has no shared scheme; `dependency-review` only reports Gradle dependencies once the `dependency-submission` job has run on `main`. |
| R8 / release minification | Not run. |
| Compose UI tests | None exist. |
| Export share to another app, import round trip on device | Not exercised on the emulator (export was reviewed and cancelled). Import/export round trips are covered by host tests only. |
| Remote publication, signing | Intentionally not done. |

The acceptance items "iOS sample builds and runs" and "Inspector works on both platforms" are therefore **not demonstrated**.

## What changed
- **Publishing:** `PublishingConventionPlugin` now uses `com.vanniktech.maven.publish`. Remote upload and signing only activate when Maven Central / signing properties are supplied. POM has description, license, SCM, developer. `LICENSE` (Apache-2.0) added. Each module publishes root + Android + iOS variants.
- **Public API:** Kotlin ABI validation baselines in each module's `api/`; about 210 public declarations documented with KDoc (entry points and contracts first).
- **Lint:** Spotless + ktlint (`ktlint_official`, max line length off) applied once across the repo (about 90 files reformatted); detekt with `config/detekt/baseline.xml` for pre-existing findings (about 190, mostly magic numbers and return count).
- **Tests:** `ConcurrencyTest` (parallel events, spans, bounded recorder), `VersionConsistencyTest` (`JourneyScope.VERSION` equals `VERSION_NAME`), measurement tests for performance. No defects were found by the new tests, so no regression tests were added.
- **CI:** `ci.yml` now runs wrapper validation, format/detekt/ABI, all host tests, iOS simulator tests, Xcode build, publish-to-Maven-Local dry run and dependency review, with read-only permissions and no publishing. `publish.yml` runs only on `v*` tags or manual dispatch, inside the `release` environment, in the upstream repo.
- **Docs:** getting-started, android-integration, ios-integration, instrumentation, privacy-security, performance, publishing, CHANGELOG, CONTRIBUTING; README, architecture, api, development updated.

## Architecture
Acyclic: `core` at the root; `diagnostics`, `export`, `flow`, `navigation`, `network-ktor`, `network-okhttp`, `database-sqldelight` depend on core; `ui-compose` depends on core + diagnostics + export; `core-noop` has no project dependency. No Android imports in `commonMain`. Buffers are bounded; cancellation is rethrown. Diagram in [architecture.md](architecture.md).

## Known limitations
1. **No-op twin and adapters are incompatible.** `devscope-core-noop` covers only the core facade; adapters depend on the real core, so a release build that uses any adapter must keep real core with `enabled = false`.
2. **Per-operation cost grows with trace size** (immutable snapshots). Default caps keep it small; thousands of events per trace are costly. Inspector mapping is super-linear (353 ms at 10,000 rows).
3. **Redaction is key/pattern-based.** Secrets in free-text values are not recognized.
4. OkHttp tracing is Android-only. No Intel iOS simulator target.
5. `samples/iosApp` enables the SDK unconditionally (no `#if DEBUG`).
6. `maxLineLength` is not enforced; detekt baseline contains pre-existing findings.
7. Placeholder POM URL, developer id and security contact (`mandanai/devscope-compose`).
8. `ui-compose` ships the `ui-tooling-preview` artifact as an `implementation` dependency of `commonMain`; not changed in this release.
9. `JourneyScope.VERSION` is a literal checked by a test, not generated.
10. Performance numbers are host-JVM only.
11. `detekt` is `2.0.0-alpha.6`, a pre-release tool, in the quality gate; pin or move to a stable release when available.
12. The Spotless reformat (about 90 files) is in the same working tree as KDoc, build and test changes, uncommitted. Nothing was committed. If you want a reviewable history, split it into a format-only commit followed by the rest.

## Local publication
```bash
./gradlew publishToMavenLocal      # artifacts in ~/.m2/repository/dev/chayaphon/journeyscope
```
Details and the pre-release checklist: [publishing.md](publishing.md).

## Recommended next steps
1. Run `iosSimulatorArm64Test`, link the framework and `xcodebuild` on a machine with Xcode (or let CI do it) and walk through the Add to Cart journey on iOS.
2. Commit a shared Xcode scheme for `samples/iosApp`.
3. Add Compose UI tests for the Inspector and a shake/debug-menu entry point in the iOS sample behind `#if DEBUG`.
4. Measure on Android and iOS devices; consider chunked trace storage to flatten per-operation cost.
5. Provide no-op adapter artifacts (or an API/impl split) so adapters can be debug-only.
6. Replace placeholder metadata, register the namespace, create signing keys, then release.
