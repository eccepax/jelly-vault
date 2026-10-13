import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Test helpers shared by the apps: the real-Vault client for contract tests (testImplementation)
// and the e2e base classes (androidTestImplementation). Never a dependency of an app itself.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.detekt)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("${rootProject.projectDir}/detekt.yml")
    autoCorrect = true
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

android {
    namespace = "org.jellyfin.mobile.testing"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Only for common's resource ids (sign-in screens); the app under test supplies them at runtime.
    compileOnly(project(":common"))
    implementation(libs.bundles.coroutines)
    api(libs.jellyfin.sdk)

    // Contract tests (JUnit 5, on the JVM): the apps' unit tests bring it; it can't be dexed into e2e APKs.
    compileOnly(libs.junit.api)

    // e2e tests (JUnit 4, instrumented)
    api(libs.junit4)
    api(libs.bundles.androidx.test)
    api(libs.androidx.test.rules)
    api(libs.compose.ui.test.junit4)
    implementation(libs.bundles.koin)

    detektPlugins(libs.detekt.formatting)
}

tasks.withType<Detekt> {
    jvmTarget = JavaVersion.VERSION_11.toString()
}
