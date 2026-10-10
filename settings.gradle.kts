pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "journeyscope"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":devscope-core")
include(":devscope-database-sqldelight")
include(":devscope-diagnostics")
include(":devscope-flow")
include(":devscope-navigation")
include(":devscope-network-ktor")
include(":devscope-network-okhttp")
include(":devscope-ui-compose")
include(":samples:composeApp")
include(":samples:androidApp")
