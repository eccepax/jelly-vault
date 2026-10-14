plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

// The vault.* convention plugins (src/main/kotlin/*.gradle.kts) apply these, so modules apply them
// through the conventions (or by id without a version), never with alias(libs.plugins...).
dependencies {
    implementation(libs.android.gradle)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.android.junit5.gradle.plugin)
}
