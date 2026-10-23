package org.jellyfin.mobile.downloads

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Downloading a book from the library: which networks it may use is asked once, then kept. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DownloadRequestsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val preferences = AudioVaultAppPreferences(context)
    private val applied = mutableListOf<Int>()
    private val downloaded = mutableListOf<List<String>>()
    private val removed = mutableListOf<String>()
    private lateinit var requests: DownloadRequests

    private fun show() {
        composeRule.setContent {
            AppTheme(VaultBrand.AudioVault) {
                requests = rememberDownloadRequests(
                    preferences = preferences,
                    applyMethod = { applied += it },
                    download = { downloaded += it },
                    remove = { removed += it },
                )
            }
        }
    }

    private fun string(resId: Int) = context.getString(resId)

    @Test
    fun `the first download asks which networks it may use, and goes once one's chosen`() {
        show()

        composeRule.runOnIdle { requests.download(listOf("a", "b")) }
        composeRule.onNodeWithText(string(R.string.network_message)).assertExists()
        // Offered as the settings offer them
        composeRule.onNodeWithText(string(R.string.wifi_only_summary)).assertExists()
        composeRule.onNodeWithText(string(R.string.mobile_data_summary)).assertExists()
        downloaded.shouldBeEmpty()
        composeRule.onNodeWithText(string(R.string.mobile_data)).performClick()
        composeRule.waitForIdle()

        preferences.downloadMethod shouldBe DownloadMethod.MOBILE_DATA
        applied shouldBe listOf(DownloadMethod.MOBILE_DATA)
        downloaded shouldBe listOf(listOf("a", "b"))
        composeRule.onNodeWithText(string(R.string.network_message)).assertDoesNotExist()
    }

    @Test
    fun `once chosen, downloads go without asking`() {
        preferences.downloadMethod = DownloadMethod.WIFI_ONLY
        show()

        composeRule.runOnIdle { requests.download(listOf("a")) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(string(R.string.network_message)).assertDoesNotExist()
        applied shouldBe listOf(DownloadMethod.WIFI_ONLY)
        downloaded shouldBe listOf(listOf("a"))
    }

    @Test
    fun `removing a book removes each of its tracks`() {
        show()

        composeRule.runOnIdle { requests.remove(listOf("a", "b")) }
        composeRule.waitForIdle()

        removed shouldBe listOf("a", "b")
    }
}
