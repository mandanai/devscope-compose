plugins {
    id("journeyscope.kmp.library")
    id("journeyscope.publishing")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.devscopeCore)
            api(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}
