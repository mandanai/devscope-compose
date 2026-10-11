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
    implementation(libs.androidx.activity.compose)

    debugImplementation(projects.devscopeCore)
    debugImplementation(projects.devscopeFlow)
    debugImplementation(projects.devscopeNavigation)
    debugImplementation(projects.devscopeNetworkKtor)
    debugImplementation(projects.devscopeDatabaseSqldelight)
    debugImplementation(projects.devscopeAndroid)

    releaseImplementation(projects.devscopeCoreNoop)
    releaseImplementation(projects.devscopeFlowNoop)
    releaseImplementation(projects.devscopeNavigationNoop)
    releaseImplementation(projects.devscopeNetworkKtorNoop)
    releaseImplementation(projects.devscopeDatabaseSqldelightNoop)
}

configurations.matching { it.name.startsWith("release") }.configureEach {
    resolutionStrategy.capabilitiesResolution.all {
        candidates.firstOrNull { it.id.displayName.contains("-noop") }?.let { select(it) }
    }
}
