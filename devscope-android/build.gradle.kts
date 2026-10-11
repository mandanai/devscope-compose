plugins {
    id("journeyscope.kmp.library.android-only")
    id("journeyscope.compose")
    id("journeyscope.publishing")
}

kotlin {
    androidLibrary {
        androidResources { enable = true }
    }
    sourceSets {
        androidMain.dependencies {
            api(projects.devscopeCore)
            api(projects.devscopeExport)
            api(projects.devscopeUiCompose)
            implementation(libs.compose.runtime)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.startup.runtime)
        }
    }
}
