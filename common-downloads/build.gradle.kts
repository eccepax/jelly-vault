plugins {
    id("vault.android.library")
}

// Downloads shared by the vault apps: Media3's DownloadManager queues them, waits for Wi-Fi and keeps
// them going in the background; each item ends up as a plain file the app can read or play.
android {
    namespace = "org.jellyfin.mobile.common.downloads"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.bundles.coroutines)
    api(libs.androidx.media3.exoplayer)
    api(libs.androidx.media3.database)

    // Testing
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.androidx.media3.test.utils)
    testImplementation(libs.androidx.media3.test.utils.robolectric)
    // Robolectric (JUnit4-based) provides android.net.Uri and friends; its tests run through the JUnit5
    // platform's vintage engine alongside the Jupiter suite.
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testRuntimeOnly(libs.junit.vintage.engine)
}
