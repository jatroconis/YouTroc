plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.youtroc.app"
    // A transitive AndroidX dep (androidx.core 1.18) requires compiling against API 36.
    // targetSdk stays at 35 — compileSdk and targetSdk are intentionally independent.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.youtroc.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            // R8 full mode + a proper keep config land in a later milestone.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // NewPipeExtractor reaches for java.nio.file APIs; required on minSdk < 33.
        isCoreLibraryDesugaringEnabled = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":data:extraction"))

    implementation(libs.androidx.activity)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
}
