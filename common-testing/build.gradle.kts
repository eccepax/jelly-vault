// Test helpers shared by the apps: the real-Vault client for contract tests (testImplementation)
// and the e2e base classes (androidTestImplementation). Never a dependency of an app itself.
plugins {
    id("vault.android.library")
}

android {
    namespace = "org.jellyfin.mobile.testing"
}

dependencies {
    // Only for common's resource ids (sign-in screens); the app under test supplies them at runtime.
    compileOnly(project(":common"))
    implementation(libs.bundles.coroutines)
    api(libs.jellyfin.sdk)
    api(libs.kotlin.serialization.json)

    // Contract tests (JUnit 5, on the JVM): the apps' unit tests bring it; it can't be dexed into e2e APKs.
    compileOnly(libs.junit.api)

    // e2e tests (JUnit 4, instrumented)
    api(libs.junit4)
    api(libs.bundles.androidx.test)
    api(libs.androidx.test.rules)
    api(libs.compose.ui.test.junit4)
    implementation(libs.bundles.koin)
}
