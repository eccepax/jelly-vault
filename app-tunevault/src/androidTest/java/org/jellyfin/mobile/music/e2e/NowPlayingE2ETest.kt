package org.jellyfin.mobile.music.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import org.jellyfin.mobile.music.nowplaying.MiniPlayerTags
import org.jellyfin.mobile.music.playback.PlaybackController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

private const val PLAYBACK_TIMEOUT_MS = 30_000L

/** Mini-player and Now Playing against real playback from the Vault. */
@PlaybackArea
class NowPlayingE2ETest : LoggedInE2ETest() {

    private val playback: PlaybackController get() = koinGet()

    private fun playLondonMay() {
        connectAndLoginToLibrary()
        openAlbum("The Purple Bird", firstTrack = "Turned To Dust (Rolling On)")
        composeRule.onNodeWithText("London May").performClick()
        composeRule.waitUntil(PLAYBACK_TIMEOUT_MS) {
            runCatching { composeRule.onNodeWithContentDescription("Pause").assertExists() }.isSuccess
        }
    }

    private fun expand() {
        composeRule.onNodeWithTag(MiniPlayerTags.ROOT).performClick()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            runCatching { composeRule.onNodeWithContentDescription("Collapse").assertExists() }.isSuccess
        }
    }

    @Test
    fun miniPlayerAppearsAndPauses() {
        playLondonMay()

        composeRule.onNodeWithTag(MiniPlayerTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Pause").performClick()

        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            runCatching { composeRule.onNodeWithContentDescription("Play").assertExists() }.isSuccess
        }
        onMain { assertFalse(playback.state.value.isPlaying) }
    }

    @Test
    fun nowPlayingSkipsToTheNextTrack() {
        playLondonMay()
        expand()

        composeRule.onNodeWithContentDescription("Next").performClick()

        waitForComposeText("Tonight With The Dogs I'm Sleeping")
        onMain { assertEquals("Tonight With The Dogs I'm Sleeping", playback.state.value.currentTrack?.title) }
    }

    @Test
    fun backCollapsesNowPlayingAndLeavesTheAlbumOpen() {
        playLondonMay()
        expand()

        pressBack()

        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            runCatching { composeRule.onNodeWithContentDescription("Collapse").assertDoesNotExist() }.isSuccess
        }
        composeRule.onNodeWithText("Turned To Dust (Rolling On)").assertIsDisplayed()
        composeRule.onNodeWithTag(MiniPlayerTags.ROOT).assertIsDisplayed()
    }
}
