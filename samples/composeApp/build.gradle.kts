plugins {
    id("journeyscope.sample.library")
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("CartDatabase") {
            packageName.set("dev.chayaphon.journeyscope.sample.shop.db")
        }
    }
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
            implementation(projects.devscopeFlow)
            implementation(projects.devscopeNavigation)
            implementation(libs.navigationevent.compose)
            implementation(projects.devscopeNetworkKtor)
            implementation(projects.devscopeDatabaseSqldelight)
            implementation(libs.ktor.client.mock)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.sqldelight.runtime)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
            implementation(projects.devscopeUiCompose)
            implementation(projects.devscopeExport)
        }
        commonTest.dependencies {
            implementation(projects.devscopeDiagnostics)
            implementation(projects.devscopeExport)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}
