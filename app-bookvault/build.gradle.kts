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

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
        optIn.add("kotlin.RequiresOptIn")
    }
}

// e2e.* properties are read from the gitignored local.properties (never committed) and passed to
// the androidTest APK only as instrumentation runner arguments — they never touch BuildConfig or
// the real app build. See the app-audiovault equivalent / LoginFlowE2ETest.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "org.jellyfin.mobile.books"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bookvault"
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
            aaptOptions.cruncherEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = releaseSigningConfig
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            aaptOptions.cruncherEnabled = false
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
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

base.archivesName.set("bookvault-v${project.getVersionName()}")

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
    implementation(libs.androidx.fragment)
    coreLibraryDesugaring(libs.androiddesugarlibs)

    // Lifecycle
    implementation(libs.bundles.androidx.lifecycle)

    // UI
    implementation(libs.google.material)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation(libs.modernandroidpreferences)
    implementation(libs.androidx.viewpager2)

    // Jetpack Compose
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

    // Room
    implementation(libs.bundles.androidx.room)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Monitoring
    implementation(libs.timber)
    debugImplementation(libs.leakcanary)

    // Testing
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockwebserver)
    // Robolectric shadows the Android framework for Fragment/Activity-level JVM tests. Robolectric
    // itself is still JUnit4-based, so its tests run through the JUnit5 platform's vintage engine
    // alongside the rest of the (JUnit5 Jupiter) suite.
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(project(":test-support"))
    testImplementation(libs.androidx.fragment.testing)
    // fragment-testing's EmptyFragmentActivity is only registered when this manifest is merged
    // into the debug variant, which is what unit tests actually run against.
    debugImplementation(libs.androidx.fragment.testing.manifest)
    testRuntimeOnly(libs.junit.vintage.engine)
    androidTestImplementation(libs.bundles.androidx.test)
    // RecyclerViewActions (e.g. clicking a library item at a given adapter position).
    androidTestImplementation(libs.androidx.test.espresso.contrib) {
        // Pulls in an old support-library appcompat transitively; this project is AndroidX-only.
        exclude(group = "com.android.support", module = "support-annotations")
        exclude(group = "com.android.support", module = "appcompat-v7")
        exclude(group = "com.android.support", module = "support-v4")
        exclude(group = "com.android.support", module = "recyclerview-v7")
    }
    // Compose UI testing (drives BookConnectFragment's ConnectScreen); Espresso above handles the
    // classic-View fragments (LoginFragment, BookLibraryFragment, BookReaderFragment) in the same
    // tests.
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

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
