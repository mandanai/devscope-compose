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
- Comments follow `.claude/rules/code-comments.md`: explain the non-obvious *why* in one line, otherwise rename or extract.
- Keep sensitive data out of spans, metadata and tests. New metadata keys should be safe by default.
- Add a regression test with every bug fix.

## Module rules
- Adapters depend on `devscope-core` only. `commonMain` must not import `android.*`.
- A module's platform source sets contain only what an API forces.
- Dependencies come from `gradle/libs.versions.toml`.

## Releasing
Maintainers follow [publishing](docs/publishing.md). Pull-request workflows never publish.
