// Settings every vault library module shares. A module adds its namespace, features and dependencies.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("io.gitlab.arturbosch.detekt")
    id("de.mannodermaus.android-junit5")
}

configureVaultKotlinDetektAndTests()

android {
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
