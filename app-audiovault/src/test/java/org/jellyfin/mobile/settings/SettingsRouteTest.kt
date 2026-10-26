package org.jellyfin.mobile.settings

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.mobile.downloads.DownloadMethod
import org.jellyfin.mobile.player.playback.FakeAudiobookController
import org.jellyfin.sdk.model.ClientInfo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import org.jellyfin.mobile.common.R as CommonR

/** Settings as AudioVault shows them: each choice is saved where the app reads it. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val appPreferences = AudioVaultAppPreferences(context)
    private val downloadManager = mockk<DownloadManager>(relaxed = true)
    private val playback = FakeAudiobookController()
    private val signingOut = CompletableDeferred<Unit>()
    private var signOuts = 0
    private var signOutFails = false
    private var backs = 0
    private var signedOut = 0

    private val viewModel by lazy {
        SettingsViewModel(
            preferences = appPreferences,
            downloadManager = downloadManager,
            playback = playback,
            signOut = {
                signOuts++
                if (signOutFails) error("Couldn't forget the account")
                signingOut.await()
            },
            app = ClientInfo("AudioVault", "9.9.9"),
        )
    }

    private fun show() {
        composeRule.setContent {
            AppTheme(VaultBrand.AudioVault) {
                SettingsRoute(onBack = { backs++ }, onSignedOut = { signedOut++ }, viewModel = viewModel)
            }
        }
    }

    private fun click(resId: Int) = composeRule.onNodeWithText(
        context.getString(resId),
    ).performScrollTo().performClick()

    @Test
    fun `about shows the app and version the app was built as`() {
        show()

        composeRule.onNodeWithText("AudioVault 9.9.9").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `turning on skip silence saves it, shows it, and the book playing skips silence at once`() {
        show()

        click(R.string.pref_skip_silence_title)
        composeRule.waitForIdle()

        appPreferences.skipSilenceEnabled shouldBe true
        playback.commands shouldBe listOf("skipSilence true")
        // Shown by reading it back: off again takes it off
        click(R.string.pref_skip_silence_title)
        composeRule.waitForIdle()
        appPreferences.skipSilenceEnabled shouldBe false
    }

    @Test
    fun `settings changed elsewhere since it was last open are shown as they are now`() {
        // Opened before (the view model outlives the screen), then changed from elsewhere
        viewModel.state.value.skipSilence shouldBe false
        appPreferences.skipSilenceEnabled = true
        show()

        // Showing it on, so the switch turns it off
        click(R.string.pref_skip_silence_title)
        composeRule.waitForIdle()

        appPreferences.skipSilenceEnabled shouldBe false
    }

    @Test
    fun `turning off hold to speed up saves it`() {
        show()

        click(R.string.pref_exoplayer_allow_press_speed_up)
        composeRule.waitForIdle()

        appPreferences.exoPlayerAllowPressSpeedUp shouldBe false
    }

    @Test
    fun `choosing mobile data saves it, and applies it to downloads already waiting`() {
        show()

        click(R.string.network_title)
        composeRule.onNodeWithText(context.getString(R.string.mobile_data)).performClick()
        composeRule.waitForIdle()

        appPreferences.downloadMethod shouldBe DownloadMethod.MOBILE_DATA
        verify { downloadManager.requirements = any() }
    }

    @Test
    fun `confirming sign out signs out, and the app is told once it's done`() {
        show()

        click(R.string.pref_sign_out)
        click(R.string.sign_out_yes)
        composeRule.waitForIdle()
        signOuts shouldBe 1
        signedOut shouldBe 0

        signingOut.complete(Unit)
        composeRule.waitForIdle()

        signedOut shouldBe 1
    }

    @Test
    fun `back goes back`() {
        show()

        composeRule.onNodeWithContentDescription(context.getString(CommonR.string.vault_back)).performClick()

        backs shouldBe 1
    }

    @Test
    fun `a sign out that fails says so, and can be tried again`() {
        signOutFails = true
        show()
        click(R.string.pref_sign_out)
        click(R.string.sign_out_yes)
        composeRule.waitForIdle()

        ShadowToast.getTextOfLatestToast() shouldBe context.getString(CommonR.string.sign_out_failed)
        signedOut shouldBe 0

        signOutFails = false
        signingOut.complete(Unit)
        click(R.string.pref_sign_out)
        click(R.string.sign_out_yes)
        composeRule.waitForIdle()

        signOuts shouldBe 2
        signedOut shouldBe 1
    }
}
