# Contributing

## Setup
JDK 17, Android SDK (compile SDK 37), and for iOS work a Mac with Xcode. Open the project in Android Studio / IntelliJ.

## Before opening a pull request
```bash
./gradlew spotlessApply          # format
./gradlew spotlessCheck detekt checkKotlinAbi
./gradlew testAndroidHostTest    # all Android host + common tests
./gradlew iosSimulatorArm64Test  # macOS only
./gradlew help --warning-mode all   # no new deprecations from this repo
```

- **Public API changes** must update the ABI baseline (`./gradlew updateKotlinAbi`) in the same commit and be documented with KDoc.
- **detekt** uses `config/detekt/baseline.xml` for pre-existing findings. Do not add to the baseline to silence new code; fix it.
- Kotlin warnings are errors. Do not use deprecated APIs; use the documented replacement.
- Keep sensitive data out of spans, metadata and tests. New metadata keys should be safe by default.
- Add a regression test with every bug fix.
- **Agent instructions** in [`ai/`](../ai/README.md) must change in the same PR as any API or behaviour they describe (start with `ai/api-cheatsheet.md` and the matching scenario). Then run `scripts/sync-skill-references.sh`; CI fails if `skills/journeyscope/references` is stale.

## Module rules
- Adapters depend on `devscope-core` only. `commonMain` must not import `android.*`.
- A module's platform source sets contain only what an API forces.
- Dependencies come from `gradle/libs.versions.toml`.

## Releasing
Maintainers follow [publishing](docs/publishing.md). Pull-request workflows never publish.
