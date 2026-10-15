package org.jellyfin.mobile.livetv.app

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.livetv.liveVaultModules
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
class LiveAppModuleTest {

    private lateinit var koin: Koin

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        // The SDK names the device after ANDROID_ID, which a real device always has and Robolectric doesn't.
        Settings.Secure.putString(app.contentResolver, Settings.Secure.ANDROID_ID, "test-device")
        koin = startKoin {
            androidContext(app)
            modules(liveVaultModules)
        }.koin
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `everything the app starts with can be built`() {
        koin.unbuildableDefinitions("test-id", SavedStateHandle()).joinToString("\n") shouldBe ""
    }

    @Test
    fun `everything the app's screens, services and helpers inject is provided`() {
        koin.unprovidedInjections(
            listOf(File("src/main/java"), File("../common/src/main/java")),
            // Only the apps that show artwork through it register this provider; LiveVault doesn't.
            notShipped = setOf("ImageProvider.kt"),
        ).joinToString("\n") shouldBe ""
    }
}
