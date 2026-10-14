plugins {
    id("vault.android.app")
    alias(libs.plugins.kotlin.ksp)
    id("org.jetbrains.kotlin.plugin.parcelize")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "org.jellyfin.mobile.livetv"

    defaultConfig {
        applicationId = "org.jellyfin.mobile.livetv"
    }

    buildFeatures {
        compose = true
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }
}

base.archivesName.set("livevault-v${project.getVersionName()}")

dependencies {
    // Common module
    implementation(project(":common"))

    // Kotlin
    implementation(libs.bundles.coroutines)

    // Core
    implementation(libs.bundles.koin)
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment)
    // Hosts common's View-based LoginFragment inside the otherwise all-Compose UI.
    implementation(libs.androidx.fragment.compose)

    // Lifecycle
    implementation(libs.bundles.androidx.lifecycle)

    // UI
    implementation(libs.google.material)
    implementation(libs.bundles.compose)
    implementation(libs.bundles.coil)
    implementation(libs.coil.compose)

    // Network
    implementation(libs.jellyfin.sdk)

    // Room
    implementation(libs.bundles.androidx.room)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Monitoring
    implementation(libs.timber)

    // Testing
    testImplementation(project(":common-testing"))
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    // Robolectric (JUnit4-based) runs Android and Compose UI tests on the JVM, through the JUnit5
    // platform's vintage engine alongside the Jupiter suite.
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(project(":common-testing"))
    androidTestImplementation(libs.bundles.androidx.test)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.compose.ui.test.junit4)
}
