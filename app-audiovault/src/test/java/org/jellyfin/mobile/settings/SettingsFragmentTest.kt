package org.jellyfin.mobile.settings

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.downloads.DownloadMethod
import org.jellyfin.mobile.player.playback.AudiobookPlaybackController
import org.jellyfin.mobile.player.playback.FakeAudiobookController
import org.jellyfin.sdk.model.ClientInfo
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.jellyfin.mobile.common.R as CommonR

/** Settings as AudioVault shows them: each choice is saved where the app reads it. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsFragmentTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val appPreferences = AudioVaultAppPreferences(context)
    private val downloadManager = mockk<DownloadManager>(relaxed = true)
    private val mainViewModel = mockk<MainViewModel>(relaxed = true)

    private val playback = FakeAudiobookController()

    @Before
    fun setUp() {
        startKoin {
            modules(
                module {
                    single { appPreferences }
                    single<AudiobookPlaybackController> { playback }
                    single { downloadManager }
                    single { ClientInfo("AudioVault", "9.9.9") }
                    viewModel { mainViewModel }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun launch() = launchFragmentInContainer<SettingsFragment>(themeResId = CommonR.style.AppTheme)

    @Test
    fun `about shows the app and version the app was built as`() {
        launch().use {
            composeRule.onNodeWithText("AudioVault 9.9.9").performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `turning on skip silence saves it, and the book playing skips silence at once`() {
        launch().use {
            composeRule.onNodeWithText(context.getString(R.string.pref_skip_silence_title)).performClick()
        }

        appPreferences.skipSilenceEnabled shouldBe true
        playback.commands shouldBe listOf("skipSilence true")
    }

    @Test
    fun `turning off hold to speed up saves it`() {
        launch().use {
            composeRule.onNodeWithText(context.getString(R.string.pref_exoplayer_allow_press_speed_up)).performClick()
        }

        appPreferences.exoPlayerAllowPressSpeedUp shouldBe false
    }

    @Test
    fun `choosing mobile data saves it, and applies it to downloads already waiting`() {
        launch().use {
            composeRule.onNodeWithText(context.getString(R.string.network_title)).performScrollTo().performClick()
            composeRule.onNodeWithText(context.getString(R.string.mobile_data)).performClick()
        }

        appPreferences.downloadMethod shouldBe DownloadMethod.MOBILE_DATA
        verify { downloadManager.requirements = any() }
    }

    @Test
    fun `confirming sign out signs out`() {
        launch().use {
            composeRule.onNodeWithText(context.getString(R.string.pref_sign_out)).performScrollTo().performClick()
            composeRule.onNodeWithText(context.getString(R.string.sign_out_yes)).performScrollTo().performClick()
            composeRule.waitForIdle()
        }

        coVerify { mainViewModel.logout() }
    }
}
