// Shared test helpers for the app modules' JVM (Robolectric) tests. Only ever a testImplementation dependency.
plugins {
    id("vault.android.library")
}

android {
    namespace = "org.jellyfin.mobile.testsupport"
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.robolectric)
    implementation(libs.roborazzi)

    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
    testImplementation(libs.bundles.kotest)
}
