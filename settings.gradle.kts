import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.mavenCentral
import org.gradle.kotlin.dsl.repositories

//settings.gradle.kts
// settings.gradle.kts
pluginManagement {
    repositories {
        // 1. Official Google repository for Android plugins
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // 2. Standard plugin sources
        mavenCentral()
        gradlePluginPortal()

        // 3. Custom JitPack repository (if you need plugins from GitHub)
        maven { url = uri("https://jitpack.io") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Add JitPack here too so your app can use GitHub libraries
        maven { url = uri("https://jitpack.io") }
    }
}


rootProject.name = "TakwaFortress"
include(":app")