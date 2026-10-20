plugins {
    id("vault.android.library")
}

// Playback pieces shared by the vault apps (device profiles today; Media3 helpers as apps need them).
android {
    namespace = "org.jellyfin.mobile.media"
}

dependencies {
    implementation(libs.jellyfin.sdk)
    implementation(libs.androidx.media3.common)
    implementation(libs.timber)

    // Testing
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.kotlin.serialization.json)
}
