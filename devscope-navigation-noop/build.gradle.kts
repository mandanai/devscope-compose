plugins {
    id("journeyscope.kmp.library")
    id("journeyscope.publishing")
    id("journeyscope.noop")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.devscopeCoreNoop)
        }
    }
}
