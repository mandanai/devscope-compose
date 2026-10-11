plugins {
    id("journeyscope.kmp.library.android-only")
    id("journeyscope.publishing")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.devscopeCore)
        }
        androidMain.dependencies {
            api(libs.okhttp)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.okhttp.mockwebserver)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
