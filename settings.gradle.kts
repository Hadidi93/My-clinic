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

rootProject.name = "MyClinic"

// :app          -> the Android application (screens, Supabase access, DI)
// :core:domain  -> plain Kotlin: data models, validation and permission rules.
//                  No Android code, so its unit tests run fast anywhere.
include(":app")
include(":core:domain")
