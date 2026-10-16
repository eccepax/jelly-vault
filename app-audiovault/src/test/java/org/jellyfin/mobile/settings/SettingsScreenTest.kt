package org.jellyfin.mobile.settings

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.R
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.mobile.downloads.DownloadMethod
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.jellyfin.mobile.common.R as CommonR

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val defaults = SettingsUiState(
        holdToSpeedUp = true,
        skipSilence = false,
        flatLibrary = true,
        downloadMethod = DownloadMethod.WIFI_ONLY,
        removeDownloadsOnSignOut = false,
    )
    private val events = mutableListOf<String>()

    private fun show(state: SettingsUiState = defaults) {
        composeRule.setContent {
            AppTheme(VaultBrand.AudioVault) {
                SettingsScreen(
                    state = state,
                    actions = SettingsActions(
                        onBack = { events += "back" },
                        onHoldToSpeedUp = { events += "holdToSpeedUp $it" },
                        onSkipSilence = { events += "skipSilence $it" },
                        onFlatLibrary = { events += "flatLibrary $it" },
                        onDownloadMethod = { events += "downloadMethod $it" },
                        onRemoveDownloadsOnSignOut = { events += "removeOnSignOut $it" },
                        onSignOut = { events += "signOut" },
                    ),
                )
            }
        }
    }

    private fun string(resId: Int) = ApplicationProvider.getApplicationContext<Application>().getString(resId)

    @Test
    fun `each switch shows its setting`() {
        show()

        composeRule.onNodeWithText(string(R.string.pref_exoplayer_allow_press_speed_up)).assertIsOn()
        composeRule.onNodeWithText(string(R.string.pref_skip_silence_title)).assertIsOff()
        composeRule.onNodeWithText(string(R.string.pref_library_global_flatten_title)).performScrollTo().assertIsOn()
    }

    @Test
    fun `tapping a switch flips its setting`() {
        show()

        composeRule.onNodeWithText(string(R.string.pref_skip_silence_title)).performClick()
        composeRule.onNodeWithText(string(R.string.pref_exoplayer_allow_press_speed_up)).performClick()
        composeRule.onNodeWithText(string(R.string.pref_library_global_flatten_title)).performScrollTo().performClick()

        events shouldBe listOf("skipSilence true", "holdToSpeedUp false", "flatLibrary false")
    }

    @Test
    fun `the flat list says how the library is shown either way`() {
        show(defaults.copy(flatLibrary = false))

        composeRule.onNodeWithText(string(R.string.pref_library_global_flatten_summary_off)).performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `the network for downloads shows the one in use, and a picker to change it`() {
        show()

        val network = string(R.string.network_title)
        composeRule.onNodeWithText(string(R.string.wifi_only)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(network).performScrollTo().performClick()
        composeRule.onAllNodes(hasText(string(R.string.wifi_only)) and isSelectable())[0].assertIsSelected()
        composeRule.onNodeWithText(string(R.string.mobile_data)).performClick()

        events shouldBe listOf("downloadMethod ${DownloadMethod.MOBILE_DATA}")
    }

    @Test
    fun `with no network chosen yet, it's Wi-Fi only`() {
        show(defaults.copy(downloadMethod = null))

        composeRule.onNodeWithText(string(R.string.wifi_only)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `removing downloads on sign-out is off until switched on`() {
        show()

        val remove = string(CommonR.string.pref_remove_downloads_on_sign_out)
        composeRule.onNodeWithText(remove).performScrollTo().assertIsOff()
        composeRule.onNodeWithText(remove).performClick()

        events shouldBe listOf("removeOnSignOut true")
    }

    @Test
    fun `sign out asks first, and cancelling keeps you signed in`() {
        show()

        composeRule.onNodeWithText(string(R.string.pref_sign_out)).performScrollTo().performClick()
        composeRule.onNodeWithText(string(CommonR.string.vault_cancel)).performScrollTo().performClick()

        events shouldBe emptyList()
        composeRule.onNodeWithText(string(R.string.pref_sign_out)).assertIsDisplayed()
    }

    @Test
    fun `confirming signs out, and says the downloads stay`() {
        show()

        composeRule.onNodeWithText(string(R.string.pref_sign_out)).performScrollTo().performClick()
        composeRule.onNodeWithText(string(R.string.sign_out_keeps_downloads)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.sign_out_yes)).performScrollTo().performClick()

        events shouldBe listOf("signOut")
    }

    @Test
    fun `the sign-out prompt says when downloads will be removed`() {
        show(defaults.copy(removeDownloadsOnSignOut = true))

        composeRule.onNodeWithText(string(R.string.pref_sign_out)).performScrollTo().performClick()

        composeRule.onNodeWithText(string(R.string.sign_out_removes_downloads)).assertIsDisplayed()
    }

    @Test
    fun `back leaves settings`() {
        show()

        composeRule.onNodeWithContentDescription(string(CommonR.string.vault_back)).performClick()

        events shouldBe listOf("back")
    }
}
