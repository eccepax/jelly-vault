import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

internal val Project.libs get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** Kotlin, detekt and unit-test settings every vault module shares (see vault.android.*). */
internal fun Project.configureVaultKotlinDetektAndTests() {
    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            optIn.add("kotlin.RequiresOptIn")
        }
    }

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom("${rootProject.projectDir}/detekt.yml")
        autoCorrect = true
    }
    dependencies {
        "detektPlugins"(libs.findLibrary("detekt-formatting").get())
    }
    tasks.withType<Detekt>().configureEach {
        jvmTarget = JavaVersion.VERSION_11.toString()
        reports {
            html.required.set(true)
            xml.required.set(false)
            txt.required.set(true)
            sarif.required.set(true)
        }
    }

    // The android-junit5 plugin auto-adds its JUnit5 Compose test artifact (android-test-compose-junit6)
    // whenever compose-ui-test is on a test classpath, and that artifact depends on
    // de.mannodermaus.junit5:core:2.0.0, which isn't published. Compose UI tests here use the JUnit4
    // createComposeRule (Robolectric unit tests + instrumented e2e), so the artifact isn't needed.
    configurations.configureEach {
        exclude(mapOf("group" to "de.mannodermaus.junit5", "module" to "android-test-compose-junit6"))
    }

    val localProperties = vaultLocalProperties()
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        useVaultContractServer(localProperties)
        testLogging {
            outputs.upToDateWhen { false }
            showStandardStreams = true
        }
    }
}
