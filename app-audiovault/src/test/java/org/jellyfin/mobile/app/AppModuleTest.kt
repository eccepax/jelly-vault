package org.jellyfin.mobile.app

import android.app.Application
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.audioVaultModules
import org.jellyfin.mobile.testing.unbuildableDefinitions
import org.jellyfin.mobile.testing.unprovidedInjections
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The app's real Koin graph: a missing binding would otherwise only show up as a crash on a device. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppModuleTest {

    private lateinit var koin: Koin

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        // The SDK names the device after ANDROID_ID, which a real device always has and Robolectric doesn't.
        Settings.Secure.putString(app.contentResolver, Settings.Secure.ANDROID_ID, "test-device")
        koin = startKoin {
            androidContext(app)
            modules(audioVaultModules)
        }.koin
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `everything the app starts with can be built`() {
        koin.unbuildableDefinitions().joinToString("\n") shouldBe ""
    }

    @Test
    fun `everything the app's screens, services and helpers inject is provided`() {
        koin.unprovidedInjections(
            listOf(
                File("src/main/java"),
                File("src/libre/java"),
                File("src/proprietary/java"),
                File("../common/src/main/java"),
            ),
        ).joinToString("\n") shouldBe ""
    }
}
