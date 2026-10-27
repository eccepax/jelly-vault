package org.jellyfin.mobile.downloads

import android.app.Application
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Downloading or removing a book from the library. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DownloadRequestsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val preferences = AudioVaultAppPreferences(context)
    private val downloaded = mutableListOf<List<String>>()
    private val removed = mutableListOf<String>()
    private lateinit var requests: DownloadRequests

    private fun show() {
        composeRule.setContent {
            AppTheme(VaultBrand.AudioVault) {
                requests = rememberDownloadRequests(
                    preferences = preferences,
                    download = { downloaded += it },
                    remove = { removed += it },
                )
            }
        }
    }

    @Test
    fun `downloading goes at once, without asking which networks (Settings has Wi-Fi only for that)`() {
        show()

        composeRule.runOnIdle { requests.download(listOf("a", "b")) }
        composeRule.waitForIdle()

        downloaded shouldBe listOf(listOf("a", "b"))
        composeRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun `removing a book removes each of its tracks`() {
        show()

        composeRule.runOnIdle { requests.remove(listOf("a", "b")) }
        composeRule.waitForIdle()

        removed shouldBe listOf("a", "b")
    }
}
