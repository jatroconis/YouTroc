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
        // NewPipeExtractor is published only on JitPack (built from the git tag); it is
        // NOT on Maven Central. Required even for transitive resolution — its nanojson
        // dependency is itself a TeamNewPipe JitPack fork.
        maven { url = uri("https://jitpack.io") }
    }
}

// The heart of the hexagon: pure Kotlin/JVM, no Android dependency.
include(":core:domain")

// Extraction adapter: also pure Kotlin/JVM (NewPipeExtractor is a Java library).
// This is where the project's #1 risk lives; validated before any Android module.
include(":data:extraction")

// Android application: the Hito 0 walking skeleton that plays a stream on the TV.
include(":app")
