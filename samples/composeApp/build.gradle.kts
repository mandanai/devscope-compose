plugins {
    id("journeyscope.sample.library")
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.devscopeCore)
            implementation(projects.devscopeUiCompose)
            implementation(projects.devscopeFlow)
            implementation(projects.devscopeNavigation)
            implementation(libs.navigationevent.compose)
            implementation(projects.devscopeNetworkKtor)
            implementation(libs.ktor.client.mock)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
        }
    }
}
