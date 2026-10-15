// Settings every vault app shares. A module adds its namespace/applicationId, features, flavors and
// dependencies, and sets base.archivesName.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("io.gitlab.arturbosch.detekt")
    id("org.jetbrains.kotlinx.kover")
    id("de.mannodermaus.android-junit5")
}

configureVaultKotlinDetektAndTests()

// e2e.* properties are read from the gitignored local.properties (never committed) and passed to
// the androidTest APK only as instrumentation runner arguments — they never touch BuildConfig or
// the real app build. See common-testing's VaultE2ETest.
val localProperties = vaultLocalProperties()

android {
    compileSdk = 36

    defaultConfig {
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    "coreLibraryDesugaring"(libs.findLibrary("androiddesugarlibs").get())
}
