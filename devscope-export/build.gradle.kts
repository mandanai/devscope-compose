plugins {
    id("journeyscope.kmp.library")
    id("journeyscope.publishing")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.devscopeCore)
        }
    }
}
