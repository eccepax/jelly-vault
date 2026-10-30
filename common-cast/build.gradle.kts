plugins {
    id("vault.android.library")
    alias(libs.plugins.compose.compiler)
}

// Chromecast for the vault apps, through the Cast framework and Google's Default Media Receiver.
// The only module that needs Google Play services: an app built without it doesn't cast.
android {
    namespace = "org.jellyfin.mobile.cast"

    buildFeatures {
        compose = true
    }

    // :common needs it, so a test APK of this module does too
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.media3.cast)
    api(libs.play.services.cast.framework)
    implementation(libs.androidx.mediarouter)
    implementation(libs.androidx.media3.session)
    implementation(libs.bundles.coroutines)
    implementation(libs.bundles.compose)
    implementation(libs.timber)
    implementation(project(":common"))
    implementation(project(":common-media"))
    implementation(libs.jellyfin.sdk)
    coreLibraryDesugaring(libs.androiddesugarlibs)

    // Testing
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.androidx.media3.exoplayer)
    testImplementation(libs.androidx.media3.test.utils)
    testImplementation(libs.androidx.media3.test.utils.robolectric)
    // Robolectric (JUnit4-based) provides android.net.Uri and a main looper; its tests run through
    // the JUnit5 platform's vintage engine alongside the Jupiter suite.
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    testRuntimeOnly(libs.junit.vintage.engine)
}
