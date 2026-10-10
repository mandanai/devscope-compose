plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(file("config/detekt/detekt.yml"))
    parallel = true
    source.setFrom(
        fileTree(rootDir) {
            include("*/src/**/*.kt", "samples/*/src/**/*.kt")
            exclude("**/build/**")
        },
    )
    baseline = file("config/detekt/baseline.xml")
}

spotless {
    kotlin {
        target("*/src/**/*.kt", "samples/*/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get()).setEditorConfigPath(rootProject.file(".editorconfig")).editorConfigOverride(mapOf("max_line_length" to "off", "ktlint_standard_max-line-length" to "disabled"))
    }
    kotlinGradle {
        target("*.kts", "*/*.kts", "samples/*/*.kts", "build-logic/**/*.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get()).setEditorConfigPath(rootProject.file(".editorconfig")).editorConfigOverride(mapOf("max_line_length" to "off", "ktlint_standard_max-line-length" to "disabled"))
    }
}
