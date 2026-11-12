pluginManagement {
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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Only for the Moonlight engine's NVIDIA Shield controller extensions (same source as upstream).
        maven("https://jitpack.io") {
            content { includeGroup("com.github.cgutman") }
        }
    }
}

rootProject.name = "GameHub"
include(":app")
include(":moonlight")
