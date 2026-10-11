plugins {
    id("journeyscope.kmp.library")
    id("journeyscope.publishing")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.devscopeCore)
        }
        androidMain.dependencies {
            api(libs.androidx.lifecycle.runtime)
        }
    }
}
