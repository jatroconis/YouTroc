plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
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

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Debug signing for now so the optimized build can be sideloaded.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
    implementation(project(":core:ui"))

    // Player destination (REQ-8..14): :app is the ONLY module that sees both
    // the concrete adapters and the feature overlay — it wires them together
    // (composition root, no DI framework — see PlaybackRoute/PlaybackViewModelFactory).
    implementation(project(":data:player"))
    implementation(project(":data:persistence"))
    implementation(project(":feature:playback"))

    // Catalog destination (RF-CAT-01..06): same composition-root pattern —
    // :app wires the concrete NewPipeVideoCatalog adapter into :feature:catalog's
    // HomeViewModel (see HomeViewModelFactory).
    implementation(project(":feature:catalog"))

    // Search destination (RF-SRCH-01..04): same composition-root pattern —
    // :app wires the concrete NewPipeVideoSearch adapter into :feature:search's
    // SearchViewModel (see SearchViewModelFactory). :app never imports
    // org.schabi.newpipe.* directly — ContentCountry is built inside
    // :data:extraction only.
    implementation(project(":feature:search"))

    // Compose for TV
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation + ViewModel wiring for the container/presentational screens.
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // LocalLifecycleOwner for the player's ON_STOP/ON_PAUSE observer (REQ-13).
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Extraction's ViewModel scope; the Media3 engine itself lives entirely
    // behind :data:player now — :app never imports androidx.media3.* directly.
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
}
