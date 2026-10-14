import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Shared test helpers for the app modules' JVM (Robolectric) tests. Only ever a testImplementation dependency.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

android {
    namespace = "org.jellyfin.mobile.testsupport"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.robolectric)
    implementation(libs.roborazzi)
}
