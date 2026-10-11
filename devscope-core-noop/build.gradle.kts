plugins {
    id("journeyscope.kmp.library")
    id("journeyscope.publishing")
}

val sharedCoreSources =
    listOf(
        "JourneyScopeConfig.kt",
        "model/DiagnosticMetadata.kt",
        "model/Status.kt",
        "model/Ids.kt",
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

// Same capability as devscope-core: Gradle fails dependency resolution if both land on one classpath.
afterEvaluate {
    configurations.matching { it.isCanBeConsumed && !it.isCanBeResolved }.configureEach {
        outgoing.capability("${project.group}:devscope-core:${project.version}")
        outgoing.capability("${project.group}:${project.name}:${project.version}")
    }
}
