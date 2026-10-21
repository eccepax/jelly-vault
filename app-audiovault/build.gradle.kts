plugins {
    id("vault.android.app")
    alias(libs.plugins.kotlin.ksp)
    id("org.jetbrains.kotlin.plugin.parcelize")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "org.jellyfin.mobile"

    defaultConfig {
        applicationId = "com.audiovault"
    }

    buildTypes {
        getByName("release") {
            aaptOptions.cruncherEnabled = false
        }
        getByName("debug") {
            aaptOptions.cruncherEnabled = false
        }
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    buildFeatures {
        compose = true
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }

    // Migration tests load each exported schema from assets, and Robolectric only sees the app's
    // own (debug) assets, so debug builds carry the schemas too; release builds don't.
    sourceSets.getByName("debug").assets.directories.add("$projectDir/schemas")
}

base.archivesName.set("audiovault-v${project.getVersionName()}")

dependencies {

    // Kotlin
    implementation(libs.bundles.coroutines)
    implementation(libs.kotlin.serialization.json)

    // Core
    implementation(libs.bundles.koin)
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)

    // Lifecycle
    implementation(libs.bundles.androidx.lifecycle)

    // UI

    // Jetpack Compose
    implementation(libs.bundles.compose)

    // Network
    val sdkVersion = findProperty("sdk.version")?.toString()
    implementation(libs.jellyfin.sdk) {
        // Change version if desired
        when (sdkVersion) {
            "local" -> version { strictly(JellyfinSdk.LOCAL) }
            "snapshot" -> version { strictly(JellyfinSdk.SNAPSHOT) }
            "unstable-snapshot" -> version { strictly(JellyfinSdk.SNAPSHOT_UNSTABLE) }
        }
    }
    implementation(libs.bundles.coil)

    // Media
    implementation(libs.androidx.media)
    implementation(libs.bundles.androidx.media3)

    // Common module
    implementation(project(":common"))
    implementation(project(":common-downloads"))
    implementation(project(":common-media"))

    // Room
    implementation(libs.bundles.androidx.room)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Monitoring
    implementation(libs.timber)
    debugImplementation(libs.leakcanary)

    // Testing
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    // Real org.json impl — the android.jar stub used for JVM unit tests throws
    // "not mocked" for every method, so anything touching org.json needs this.
    testImplementation(libs.org.json)
    // Robolectric shadows the Android framework for Fragment/Activity-level JVM tests. Robolectric
    // itself is still JUnit4-based, so its tests run through the JUnit5 platform's vintage engine
    // alongside the rest of the (JUnit5 Jupiter) suite.
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    // A real ExoPlayer on fake media and a fake clock, for the playback service's player
    testImplementation(libs.androidx.media3.test.utils)
    testImplementation(libs.androidx.media3.test.utils.robolectric)
    testImplementation(project(":common-testing"))
    testImplementation(project(":test-support"))
    // Migration tests (see the debug assets above)
    testImplementation(libs.androidx.room.testing)
    testRuntimeOnly(libs.junit.vintage.engine)
    androidTestImplementation(project(":common-testing"))
    androidTestImplementation(libs.bundles.androidx.test)
    // Compose UI testing: every screen is Compose
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

androidComponents {
    // Robolectric Compose tests rely on the debug-only ui-test-manifest, so unit tests run against debug builds only
    beforeVariants(selector().withBuildType("release")) { variant ->
        variant.enableUnitTest = false
    }
}
