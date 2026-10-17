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
        viewBinding = true
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
    implementation(libs.androidx.fragment)

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
    testImplementation(project(":common-testing"))
    testImplementation(project(":test-support"))
    testImplementation(project(":common-testing"))
    // Migration tests (see the debug assets above)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.fragment.testing)
    // fragment-testing's EmptyFragmentActivity is only registered when this manifest is merged
    // into the debug variant, which is what unit tests actually run against.
    debugImplementation(libs.androidx.fragment.testing.manifest)
    testRuntimeOnly(libs.junit.vintage.engine)
    androidTestImplementation(project(":common-testing"))
    androidTestImplementation(libs.bundles.androidx.test)
    // RecyclerViewActions (e.g. clicking a library item at a given adapter position).
    androidTestImplementation(libs.androidx.test.espresso.contrib) {
        // Pulls in an old support-library appcompat transitively; this project is AndroidX-only.
        exclude(group = "com.android.support", module = "support-annotations")
        exclude(group = "com.android.support", module = "appcompat-v7")
        exclude(group = "com.android.support", module = "support-v4")
        exclude(group = "com.android.support", module = "recyclerview-v7")
    }
    // Compose UI testing (drives ConnectFragment's ConnectScreen); Espresso above handles the
    // classic-View fragments (LoginFragment, LibraryFragment, PlayerFragment) in the same tests.
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

androidComponents {
    // Robolectric fragment tests rely on the debug-only fragment-testing manifest, so unit tests run against debug builds only
    beforeVariants(selector().withBuildType("release")) { variant ->
        variant.enableUnitTest = false
    }
}
