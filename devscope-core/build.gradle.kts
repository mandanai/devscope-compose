plugins {
    id("journeyscope.kmp.library")
    id("journeyscope.publishing")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }
    }
}
