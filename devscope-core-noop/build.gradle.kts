plugins {
    id("journeyscope.kmp.library")
    id("journeyscope.publishing")
    id("journeyscope.noop")
}

val sharedCoreSources =
    listOf(
        "JourneyScopeConfig.kt",
        "model/DiagnosticMetadata.kt",
        "model/Status.kt",
        "model/Ids.kt",
        "network/NetworkModels.kt",
        "network/NetworkTraceConfig.kt",
    )

val syncSharedCore =
    tasks.register<Sync>("syncSharedCoreSources") {
        from(project(":devscope-core").layout.projectDirectory.dir("src/commonMain/kotlin/dev/chayaphon/journeyscope/core")) {
            include(sharedCoreSources)
        }
        into(layout.buildDirectory.dir("generated/sharedCore/dev/chayaphon/journeyscope/core"))
    }

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/sharedCore").map { it })
        }
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }
    }
}

tasks.configureEach {
    if (name.startsWith("compile") || name.endsWith("sourcesJar", ignoreCase = true) || name.startsWith("ksp") || name == "assemble") {
        dependsOn(syncSharedCore)
    }
}
