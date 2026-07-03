plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.youtroc.data.player"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Robolectric's @RunWith(RobolectricTestRunner::class) needs the merged
    // manifest + resources on the unit test classpath (R1 gate: DashManifestParseGateTest).
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Depends inward on the domain — implements its MediaPlayer port over Media3.
    // Deliberately NOT a dependency on :data:extraction: NewPipe types never
    // reach this adapter, only the opaque PlaybackManifest carrier.
    implementation(project(":core:domain"))

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.kotlinx.coroutines.core)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)

    // R1 gate (Media3 MPD-parse): DashManifestParseGateTest needs android.net.Uri +
    // XmlPullParser, only available under Robolectric's shadow Android runtime.
    // JUnit4 + vintage-engine bridges Robolectric's @RunWith into the same
    // JUnit Platform run as the rest of this module's JUnit5 tests.
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
