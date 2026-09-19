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
    }
}

rootProject.name = "meshhop-app"

include(":core-protocol")
include(":core-domain")
include(":core-transport")
include(":core-data")
include(":ui-core")
include(":feature-onboarding")
include(":feature-messaging")
include(":feature-contacts")
include(":transport-android")
include(":app")
