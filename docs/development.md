# Development

Requirements: JDK 17 (toolchain auto-provisioned), Android SDK with platform 37, Xcode 26+ for iOS (macOS only).

## Android
```
./gradlew :samples:androidApp:assembleDebug
./gradlew :samples:androidApp:installDebug
```

## iOS (macOS + Xcode)
1. `./gradlew :samples:composeApp:linkDebugFrameworkIosSimulatorArm64`
2. Open `samples/iosApp/iosApp.xcodeproj`, pick a simulator, Run. The "Compile Kotlin Framework" build phase calls `embedAndSignAppleFrameworkForXcode`.
Deployment target is iOS 16.0.

## Tests
```
./gradlew :devscope-core:testAndroidHostTest
./gradlew :devscope-core:iosSimulatorArm64Test   # macOS + Xcode
./gradlew :devscope-core:compileCommonMainKotlinMetadata :devscope-ui-compose:compileCommonMainKotlinMetadata
```

## Publishing
Group and version come from `gradle.properties` (`GROUP`, `VERSION_NAME`). `./gradlew publishToMavenLocal` for a local check. Signing and remote repositories are not configured yet.
