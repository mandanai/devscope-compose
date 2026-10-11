plugins {
    id("journeyscope.sample.android-app")
}

android {
    namespace = "dev.chayaphon.journeyscope.sample.android"

    defaultConfig {
        applicationId = "dev.chayaphon.journeyscope.sample"
    }
}

dependencies {
    implementation(projects.samples.composeApp)
    implementation(projects.devscopeFlow)
    implementation(projects.devscopeAndroid)
    implementation(libs.androidx.activity.compose)
}
