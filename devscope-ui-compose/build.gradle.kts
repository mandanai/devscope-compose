plugins {
    id("journeyscope.kmp.library")
    id("journeyscope.compose")
    id("journeyscope.publishing")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.devscopeCore)
            api(projects.devscopeDiagnostics)
            api(projects.devscopeExport)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.navigationevent.compose)
            implementation(libs.compose.ui.tooling.preview)
        }
    }
}
