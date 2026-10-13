package org.jellyfin.mobile.music.e2e

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.jellyfin.mobile.music.app.PlaybackPreferences
import org.jellyfin.mobile.music.domain.StreamingQuality
import org.jellyfin.mobile.music.playback.PlaybackController
import org.jellyfin.sdk.api.client.ApiClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.jellyfin.mobile.common.R as CommonR

@AccountArea
class SettingsE2ETest : LoggedInE2ETest() {

    private val playback: PlaybackController get() = koinGet()

    private fun openSettings() {
        waitForComposeText("Recently added")
        composeRule.onNodeWithContentDescription("Settings").performClick()
        waitForComposeText("Streaming quality")
    }

    private fun waitUntilPlaying() {
        composeRule.waitUntil(30_000L) { onMain { playback.state.value.isPlaying && playback.positionMs > 1_000L } }
    }

    @Test
    fun settingsShowWhoIsSignedInAndWhere() {
        connectAndLogin()
        openSettings()

        composeRule.onNodeWithText(e2eUsername()).assertExists()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeRule.onAllNodes(hasText(e2eHostname(), substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun dataSaverIsKeptAndItsTranscodedStreamPlays() {
        connectAndLogin()
        openSettings()

        // Streaming quality is the first list; Downloads has its own below it.
        composeRule.onAllNodes(hasText("Data saver") and isSelectable())[0].performClick()
        assertEquals(StreamingQuality.LOW, koinGet<PlaybackPreferences>().streamingQuality)

        // The Purple Bird is FLAC, so at 128 kbps the server transcodes it: this proves the player
        // copes with what the server sends back.
        pressBack()
        composeRule.onNodeWithText("Library").performClick()
        openAlbum("The Purple Bird", firstTrack = "Turned To Dust (Rolling On)")
        composeRule.onNodeWithText("Turned To Dust (Rolling On)").performClick()
        waitUntilPlaying()
    }

    @Test
    fun signingOutStopsPlaybackAndReturnsToSignInAndYouCanSignBackIn() {
        connectAndLoginToLibrary()
        openAlbum("The Purple Bird", firstTrack = "Turned To Dust (Rolling On)")
        composeRule.onNodeWithText("Turned To Dust (Rolling On)").performClick()
        waitUntilPlaying()
        composeRule.onNodeWithText("Home").performClick()
        openSettings()

        composeRule.onNodeWithText("Sign out").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Yes, sign out").performScrollTo().performClick()

        waitForView(withId(CommonR.id.editUsername))
        onMain {
            assertTrue(playback.state.value.queue.entries.isEmpty())
            assertTrue(!playback.state.value.isPlaying)
        }
        assertNull(koinGet<ApiClient>().accessToken)

        // Signing back in lands on a working Home, in the fresh activity.
        signIn()
        waitForComposeText("Recently added")
    }
}
