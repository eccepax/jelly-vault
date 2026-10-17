package org.jellyfin.mobile.music.e2e

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.jellyfin.mobile.music.playback.PlaybackController
import org.jellyfin.mobile.testing.VaultFixtures.PatternSuite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PLAYBACK_TIMEOUT_MS = 30_000L

/**
 * Real playback: the app streams from the test server through the PlaybackService's ExoPlayer.
 * "Playing" means the position actually advances, not just that play was requested.
 */
@PlaybackArea
class PlaybackE2ETest : LoggedInE2ETest() {

    private val playback: PlaybackController get() = koinGet()

    private fun openPatternSuite() {
        connectAndLoginToLibrary()
        openAlbum(PatternSuite.TITLE, firstTrack = PatternSuite.FIRST_TRACK)
    }

    private fun waitUntilPlayingPast(positionMs: Long) {
        composeRule.waitUntil(PLAYBACK_TIMEOUT_MS) {
            onMain { playback.state.value.isPlaying && playback.positionMs > positionMs }
        }
    }

    @Smoke
    @Test
    fun tappingATrackStreamsIt() {
        openPatternSuite()

        composeRule.onNodeWithText(PatternSuite.KNOWN_SONG).performClick()

        waitUntilPlayingPast(1_000L)
        onMain {
            assertEquals(PatternSuite.KNOWN_SONG, playback.state.value.currentTrack?.title)
            assertEquals(12, playback.state.value.queue.tracks.size)
        }
    }

    @Test
    fun shufflePlaysTheWholeAlbumShuffled() {
        openPatternSuite()

        composeRule.onNodeWithText("Shuffle").performClick()

        waitUntilPlayingPast(1_000L)
        onMain {
            assertTrue(playback.state.value.queue.isShuffled)
            assertEquals(12, playback.state.value.queue.tracks.size)
        }
    }
}
