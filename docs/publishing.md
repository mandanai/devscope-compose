# Publishing

Modules published (group `dev.chayaphon.journeyscope`, shared version from `VERSION_NAME` in `gradle.properties`):
`devscope-core`, `devscope-core-noop`, `devscope-flow-noop`, `devscope-navigation-noop`, `devscope-network-ktor-noop`, `devscope-database-sqldelight-noop`, `devscope-ui-compose`, `devscope-network-ktor`, `devscope-network-okhttp` (Android only), `devscope-flow`, `devscope-navigation`, `devscope-database-sqldelight`, `devscope-diagnostics`, `devscope-export`.

Each KMP module publishes a root module plus `-android`, `-iosarm64` and `-iossimulatorarm64` variants with Gradle module metadata, a sources jar and a javadoc jar.

## Local publication and consumption

```bash
./gradlew publishToMavenLocal
```
Artifacts land in `~/.m2/repository/dev/chayaphon/journeyscope/`. In a consumer project:

```kotlin
repositories { mavenLocal(); mavenCentral(); google() }
dependencies { implementation("dev.chayaphon.journeyscope:devscope-core:0.1.0") }
```

## Remote publishing
Configured through the vanniktech `com.vanniktech.maven.publish` plugin in `PublishingConventionPlugin`. Remote upload and signing activate **only** when these Gradle properties are present:

- `mavenCentralUsername`, `mavenCentralPassword`
- `signingInMemoryKey`, `signingInMemoryKeyPassword`

Without them the build can only publish to Maven Local.

Locally, put the four properties in the git-ignored `local.properties` and run `scripts/publish.sh` (it exports them as `ORG_GRADLE_PROJECT_*` and runs `publishToMavenCentral`; pass other Gradle tasks as arguments). In CI they come from the `release` environment secrets `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY`, `SIGNING_KEY_PASSWORD`. `signingInMemoryKey` is stored in `local.properties` as one line with `\n` escapes (the script expands them); the `SIGNING_KEY` secret must hold the real multi-line armored key, since environment variables are not unescaped. The public key must be on a keyserver. CI runs a local dry run on every change; `.github/workflows/publish.yml` runs only on a `v*` tag or manual dispatch, inside the protected `release` environment, and only in the `mandanai/devscope-compose` repository.

## Before the first release (TODO)
- Register the `dev.chayaphon.journeyscope` namespace on Maven Central and create the signing key.
- Replace `-SNAPSHOT` with a release version and update `CHANGELOG.md`.
- Run `./gradlew spotlessCheck detekt checkKotlinAbi` and refresh API baselines (`updateKotlinAbi`) only for intentional API changes.

## Public API compatibility
Kotlin's built-in ABI validation stores baselines in each module's `api/` directory. `checkKotlinAbi` fails CI when the public API changes without an updated baseline.
