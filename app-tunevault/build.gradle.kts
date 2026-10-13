import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.app)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.detekt)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.android.junit5)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("${rootProject.projectDir}/detekt.yml")
    autoCorrect = true
}

// The android-junit5 plugin auto-adds its JUnit5 Compose test artifact (android-test-compose-junit6)
// whenever compose-ui-test is on a test classpath, and that artifact depends on
// de.mannodermaus.junit5:core:2.0.0, which isn't published. Compose UI tests here use the JUnit4
// createComposeRule (Robolectric unit tests + instrumented e2e), same as the sibling apps, so the
// artifact isn't needed.
configurations.configureEach {
    exclude(group = "de.mannodermaus.junit5", module = "android-test-compose-junit6")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
        optIn.add("kotlin.RequiresOptIn")
    }
}

// e2e.* properties are read from the gitignored local.properties (never committed) and passed to
// the androidTest APK only as instrumentation runner arguments — they never touch BuildConfig or
// the real app build. Same setup as app-audiovault / app-bookvault.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "org.jellyfin.mobile.music"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tunevault"
        minSdk = 26
        targetSdk = 36
        versionName = project.getVersionName()
        versionCode = getVersionCode(versionName!!)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        testInstrumentationRunnerArguments["e2eHostname"] =
            localProperties.getProperty("e2e.hostname", "http://10.0.2.2:8096")
        testInstrumentationRunnerArguments["e2eUsername"] = localProperties.getProperty("e2e.username", "")
        testInstrumentationRunnerArguments["e2ePassword"] = localProperties.getProperty("e2e.password", "")
    }

    val releaseSigningConfig = SigningHelper.loadSigningConfig(project)?.let { config ->
        signingConfigs.create("release") {
            storeFile = config.storeFile
            storePassword = config.storePassword
            keyAlias = config.keyAlias
            keyPassword = config.keyPassword
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = releaseSigningConfig
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    lint {
        lintConfig = file("$rootDir/android-lint.xml")
        abortOnError = true
        sarifReport = true
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

base.archivesName.set("tunevault-v${project.getVersionName()}")

dependencies {
    // Common module
    implementation(project(":common"))

    // Kotlin
    implementation(libs.bundles.coroutines)
    implementation(libs.kotlin.serialization.json)

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
    coreLibraryDesugaring(libs.androiddesugarlibs)

    // Lifecycle
    implementation(libs.bundles.androidx.lifecycle)

    // UI
    implementation(libs.google.material)
    implementation(libs.bundles.compose)

    // Network
    val sdkVersion = findProperty("sdk.version")?.toString()
    implementation(libs.jellyfin.sdk) {
        when (sdkVersion) {
            "local" -> version { strictly(JellyfinSdk.LOCAL) }
            "snapshot" -> version { strictly(JellyfinSdk.SNAPSHOT) }
            "unstable-snapshot" -> version { strictly(JellyfinSdk.SNAPSHOT_UNSTABLE) }
        }
    }
    implementation(libs.bundles.coil)
    implementation(libs.coil.compose)
    // Drag-to-reorder for the queue.
    implementation(libs.reorderable)

    // Media
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)

    // Room
    implementation(libs.bundles.androidx.room)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Monitoring
    implementation(libs.timber)
    debugImplementation(libs.leakcanary)

    // Testing
    testImplementation(project(":common-testing"))
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    // Robolectric (JUnit4-based) runs Compose UI tests on the JVM; its tests run through the
    // JUnit5 platform's vintage engine alongside the Jupiter suite.
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(project(":test-support"))
    testImplementation(libs.compose.ui.test.junit4)
    // TestExoPlayer + fake media sources: a real player driving fake media under Robolectric.
    testImplementation(libs.androidx.media3.test.utils)
    testImplementation(libs.androidx.media3.test.utils.robolectric)
    testRuntimeOnly(libs.junit.vintage.engine)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.bundles.androidx.test)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.compose.ui.test.junit4)

    // Formatting rules for detekt
    detektPlugins(libs.detekt.formatting)
}

tasks {
    withType<Detekt> {
        jvmTarget = JavaVersion.VERSION_11.toString()

        reports {
            html.required.set(true)
            xml.required.set(false)
            txt.required.set(true)
            sarif.required.set(true)
        }
    }

    withType<Test> {
        useJUnitPlatform()
        useVaultContractServer(localProperties)
        testLogging {
            outputs.upToDateWhen { false }
            showStandardStreams = true
        }
    }
}

androidComponents {
    // Robolectric fragment tests rely on the debug-only fragment-testing manifest, so unit tests run against debug builds only
    beforeVariants(selector().withBuildType("release")) { variant ->
        variant.enableUnitTest = false
    }
}
