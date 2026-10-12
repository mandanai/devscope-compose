# Contributing to JourneyScope

Thanks for helping. Please read the [Code of Conduct](CODE_OF_CONDUCT.md) first. Report vulnerabilities privately as described in [SECURITY.md](SECURITY.md), never in a public issue.

## Getting started

Requirements: JDK 17, Android SDK (compile SDK 37), and a Mac with Xcode 26+ for iOS work. See [development](docs/development.md).

1. Fork the repository on GitHub.
2. Clone your fork and add the upstream remote:
   ```bash
   git clone https://github.com/<your-user>/devscope-compose.git
   cd devscope-compose
   git remote add upstream https://github.com/mandanai/devscope-compose.git
   ```
3. Branch from `develop`:
   ```bash
   git fetch upstream
   git switch -c feature/<short-name> upstream/develop   # fixes: bugfix/<short-name>
   ```
4. Commit in small, focused steps. Conventional prefixes (`feat:`, `fix:`, `docs:`, `chore:`) match the existing history.
5. Push to your fork and open a Pull Request against `develop`.

## Project structure

Each `devscope-*` directory is a module; [modules](docs/modules.md) and [architecture](docs/architecture.md) describe their roles.

- Adapters depend on `devscope-core` only. `commonMain` must not import `android.*`.
- Platform source sets hold only what an API forces.
- Every runtime module that ships in release builds has a `-noop` twin; keep the twins binary compatible (`scripts/check-noop-abi.sh`).
- Dependencies and versions come from `gradle/libs.versions.toml`. Justify any new dependency in the PR.
- `build-logic/` holds the convention plugins; prefer extending them over per-module build logic.

## Development guidelines

Before opening a PR:

```bash
./gradlew spotlessApply                       # format (ktlint, official style)
./gradlew spotlessCheck detekt checkKotlinAbi
./gradlew testAndroidHostTest
./gradlew iosSimulatorArm64Test               # macOS only
./gradlew help --warning-mode all             # no new deprecations from this repo
```

- Follow the Kotlin official style as enforced by `.editorconfig`, ktlint and detekt. Do not grow `config/detekt/baseline.xml`; fix findings instead.
- Public API changes update the ABI baseline (`./gradlew updateKotlinAbi`) in the same commit and carry KDoc.
- Do not use deprecated APIs; use the documented replacement.
- Add a regression test for every bug fix and tests for new behavior.
- Keep sensitive data out of spans, metadata and tests. New metadata keys need a safe default; see [privacy and security](docs/privacy-security.md).
- Agent instructions in [`ai/`](ai/README.md) change in the same PR as any API or behavior they describe. Then run `scripts/sync-skill-references.sh`; CI fails if `skills/` is stale.
- Update the relevant page in [`docs/`](docs/) and `docs/CHANGELOG.md` for user-visible changes.

## Pull request requirements

- Explain the purpose and scope of the change and link related issues (`Closes #123`).
- Include tests and documentation updates where relevant.
- Keep the PR focused; avoid unrelated refactors or renames.
- Never commit secrets, tokens, signing keys or `local.properties`.
- All CI checks must pass.
- Respond to review feedback; push follow-up commits to the same branch.

## Review and approval

A maintainer reviews each PR. Changes to workflows, Gradle build files, publishing and security files require approval from a code owner (see [`.github/CODEOWNERS`](.github/CODEOWNERS)). A PR is merged once it has maintainer approval and passing checks. Review time depends on maintainer availability and is not guaranteed.

## Releasing

Maintainers only; see [publishing](docs/publishing.md). Pull request workflows never publish.
