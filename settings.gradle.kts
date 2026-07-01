rootProject.name = "youtroc"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Modules may not declare their own repositories: keep resolution centralized here.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// The heart of the hexagon: pure Kotlin/JVM, no Android dependency.
// Android modules (:app, :data:player, ...) are added once the Android SDK is in place.
include(":core:domain")
