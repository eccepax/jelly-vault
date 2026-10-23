// Shared test helpers for the app modules' JVM (Robolectric) tests. Only ever a testImplementation dependency.
plugins {
    id("vault.android.library")
}

android {
    namespace = "org.jellyfin.mobile.testsupport"
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.junit4)
    implementation(libs.kotest.assertions)
    implementation(libs.robolectric)
    implementation(libs.roborazzi)
}
