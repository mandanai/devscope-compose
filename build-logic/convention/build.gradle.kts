plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(
        libs.versions.jdk
            .get()
            .toInt(),
    )
}

dependencies {
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
    compileOnly(libs.compose.compiler.gradle.plugin)
    compileOnly(libs.android.gradle.plugin)
    implementation(libs.vanniktech.publish.plugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "journeyscope.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("androidKmpLibrary") {
            id = "journeyscope.kmp.library.android-only"
            implementationClass = "AndroidKmpLibraryConventionPlugin"
        }
        register("compose") {
            id = "journeyscope.compose"
            implementationClass = "ComposeConventionPlugin"
        }
        register("publishing") {
            id = "journeyscope.publishing"
            implementationClass = "PublishingConventionPlugin"
        }
        register("noop") {
            id = "journeyscope.noop"
            implementationClass = "NoopConventionPlugin"
        }
        register("sampleLibrary") {
            id = "journeyscope.sample.library"
            implementationClass = "SampleLibraryConventionPlugin"
        }
        register("sampleAndroidApp") {
            id = "journeyscope.sample.android-app"
            implementationClass = "SampleAndroidAppConventionPlugin"
        }
    }
}
