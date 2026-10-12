# Development

Requirements: JDK 17 (toolchain auto-provisioned), Android SDK with platform 37, Xcode 26+ for iOS (macOS only).

## Android
```
./gradlew :samples:androidApp:assembleDebug
./gradlew :samples:androidApp:installDebug
```

## iOS (macOS + Xcode)
1. `./gradlew :samples:composeApp:linkDebugFrameworkIosSimulatorArm64`
2. Open `samples/iosApp/iosApp.xcodeproj`, pick a simulator, Run. The "Compile Kotlin Framework" build step calls `embedAndSignAppleFrameworkForXcode`.
Deployment target is iOS 16.0.

## Tests
```
./gradlew :devscope-core:testAndroidHostTest
./gradlew :devscope-core:iosSimulatorArm64Test   # macOS + Xcode
./gradlew :devscope-core:compileCommonMainKotlinMetadata :devscope-ui-compose:compileCommonMainKotlinMetadata
```

## Documentation site
```
python3 -m venv .venv-docs
.venv-docs/bin/pip install -r requirements-docs.txt
.venv-docs/bin/mkdocs serve          # http://127.0.0.1:8000
.venv-docs/bin/mkdocs build --strict # output in build/site
```
The site is built from `docs/` and `mkdocs.yml`; CI runs the strict build on every docs change and deploys `main` to GitHub Pages.

## Publishing
Group and version come from `gradle.properties` (`GROUP`, `VERSION_NAME`). `./gradlew publishToMavenLocal` for a local check. Remote upload and signing activate only when Maven Central credentials are supplied; see [publishing.md](publishing.md).
