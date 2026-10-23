package org.jellyfin.mobile.music.account

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.music.catalog.Account
import org.jellyfin.mobile.music.domain.StreamingQuality
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val signedIn = SettingsUiState(
        account = Account(userName = "alice", serverName = "The Vault", serverUrl = "http://vault:8096"),
        streamingQuality = StreamingQuality.ORIGINAL,
        // Not the app's real name, so the screen is seen to show the one it's given
        appName = "TuneVault Test",
        appVersion = "1.2.3",
    )
    private val events = mutableListOf<String>()

    private fun show(state: SettingsUiState = signedIn) {
        composeRule.setContent {
            SettingsScreen(
                state = state,
                onBack = { events += "back" },
                onStreamingQuality = { events += "quality $it" },
                onSignOut = { events += "signOut" },
                onShowSleepTimer = { events += "showSleepTimer $it" },
                onVolumeLevelling = { events += "levelling $it" },
                downloads = DownloadSettingsActions(
                    onQuality = { events += "download quality $it" },
                    onWifiOnly = { events += "wifiOnly $it" },
                    onRemoveOnSignOut = { events += "removeOnSignOut $it" },
                    onRemoveAll = { events += "removeAll" },
                ),
            )
        }
    }

    /** The streaming list comes first, so its options are the first match; downloads' the second. */
    private fun qualityOption(label: String) = composeRule.onAllNodes(hasText(label) and isSelectable())[0]
    private fun downloadQualityOption(label: String) = composeRule.onAllNodes(hasText(label) and isSelectable())[1]

    @Test
    fun `shows the account, server and version`() {
        show()

        composeRule.onNodeWithText("alice").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("The Vault · http://vault:8096").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("TuneVault Test 1.2.3").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `there's a way to report a bug`() {
        show()

        composeRule.onNodeWithText("Report a bug").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the saved quality is selected and another can be chosen`() {
        show(signedIn.copy(streamingQuality = StreamingQuality.NORMAL))

        qualityOption("Normal").assertIsSelected()
        qualityOption("Data saver").performClick()

        events shouldBe listOf("quality LOW")
    }

    @Test
    fun `sign out asks first, and cancelling keeps you signed in`() {
        show()

        composeRule.onNodeWithText("Sign out").performScrollTo().performClick()
        composeRule.onNodeWithText("Sign out of The Vault?").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed().performClick()

        events shouldBe emptyList()
        composeRule.onNodeWithText("Sign out of The Vault?").assertDoesNotExist()
    }

    @Test
    fun `confirming signs out`() {
        show()

        composeRule.onNodeWithText("Sign out").performScrollTo().performClick()
        composeRule.onNodeWithText("Yes, sign out").assertIsDisplayed().performClick()

        events shouldBe listOf("signOut")
    }

    @Test
    fun `while signing out, it says so instead of offering the button`() {
        show(signedIn.copy(signingOut = true))

        composeRule.onNodeWithText("Signing out…").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Sign out").assertDoesNotExist()
    }

    @Test
    fun `back goes back`() {
        show()

        composeRule.onNodeWithContentDescription("Back").performClick()

        events shouldBe listOf("back")
    }

    @Test
    fun `download quality is its own choice`() {
        show(signedIn.copy(downloadQuality = StreamingQuality.HIGH))

        downloadQualityOption("High").performScrollTo().assertIsSelected()
        qualityOption("Original").assertIsSelected()
        downloadQualityOption("Data saver").performScrollTo().performClick()

        events shouldBe listOf("download quality LOW")
    }

    @Test
    fun `downloads can be set to go when signing out`() {
        show()

        composeRule.onNodeWithText("Remove downloads on sign out").performScrollTo().performClick()

        events shouldBe listOf("removeOnSignOut true")
    }

    @Test
    fun `wi-fi only can be switched off`() {
        show()

        composeRule.onNodeWithText("Download over Wi-Fi only").performScrollTo().performClick()

        events shouldBe listOf("wifiOnly false")
    }

    @Test
    fun `shows the space used, and removing all asks first`() {
        show(signedIn.copy(downloadBytes = 1_500_000_000L, hasDownloads = true))

        composeRule.onNodeWithText("Downloads use 1.5 GB").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Remove all downloads").performScrollTo().performClick()
        events shouldBe emptyList()
        composeRule.onNodeWithText("Yes, remove all").performClick()

        events shouldBe listOf("removeAll")
    }

    @Test
    fun `with nothing downloaded there is nothing to remove`() {
        show(signedIn.copy(downloadBytes = 0L))

        composeRule.onNodeWithText("Nothing downloaded").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Remove all downloads").assertDoesNotExist()
    }

    @Test
    fun `signing out warns that downloads go too`() {
        show()

        composeRule.onNodeWithText("Sign out").performScrollTo().performClick()

        composeRule.onNodeWithText("Downloads on this device will be removed.").assertIsDisplayed()
    }

    @Test
    fun `the sleep timer can be hidden`() {
        show(signedIn.copy(showSleepTimer = true))

        composeRule.onNodeWithText("Show sleep timer").performScrollTo().performClick()

        events shouldBe listOf("showSleepTimer false")
    }

    @Test
    fun `volume levelling can be switched off`() {
        show(signedIn.copy(volumeLevelling = true))

        composeRule.onNodeWithText("Volume levelling").performScrollTo().performClick()

        events shouldBe listOf("levelling false")
    }
}
