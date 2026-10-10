plugins {
    id("journeyscope.sample.android-app")
}

android {
    namespace = "dev.journeyscope.sample.android"

    defaultConfig {
        applicationId = "dev.journeyscope.sample"
    }
}

dependencies {
    implementation(projects.samples.composeApp)
    implementation(projects.devscopeFlow)
    implementation(libs.androidx.activity.compose)
}
