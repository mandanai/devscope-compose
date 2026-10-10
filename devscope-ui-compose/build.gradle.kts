plugins {
    id("journeyscope.kmp.library")
    id("journeyscope.compose")
    id("journeyscope.publishing")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.devscopeCore)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
        }
    }
}
