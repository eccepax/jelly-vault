package org.jellyfin.mobile.music.e2e

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.jellyfin.mobile.music.playback.PlaybackController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PLAYBACK_TIMEOUT_MS = 30_000L

/**
 * Real playback: the app streams from the real Vault through the PlaybackService's ExoPlayer.
 * "Playing" means the position actually advances, not just that play was requested.
 */
@PlaybackArea
class PlaybackE2ETest : LoggedInE2ETest() {

    private val playback: PlaybackController get() = koinGet()

    private fun openPurpleBird() {
        connectAndLoginToLibrary()
        openAlbum("The Purple Bird", firstTrack = "Turned To Dust (Rolling On)")
    }

    private fun waitUntilPlayingPast(positionMs: Long) {
        composeRule.waitUntil(PLAYBACK_TIMEOUT_MS) {
            onMain { playback.state.value.isPlaying && playback.positionMs > positionMs }
        }
    }

    @Smoke
    @Test
    fun tappingATrackStreamsIt() {
        openPurpleBird()

        composeRule.onNodeWithText("London May").performClick()

        waitUntilPlayingPast(1_000L)
        onMain {
            assertEquals("London May", playback.state.value.currentTrack?.title)
            assertEquals(12, playback.state.value.queue.tracks.size)
        }
    }

    @Test
    fun shufflePlaysTheWholeAlbumShuffled() {
        openPurpleBird()

        composeRule.onNodeWithText("Shuffle").performClick()

        waitUntilPlayingPast(1_000L)
        onMain {
            assertTrue(playback.state.value.queue.isShuffled)
            assertEquals(12, playback.state.value.queue.tracks.size)
        }
    }
}
