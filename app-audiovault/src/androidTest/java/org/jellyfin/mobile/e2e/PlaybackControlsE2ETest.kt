package org.jellyfin.mobile.e2e

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.jellyfin.mobile.R
import org.jellyfin.mobile.player.ui.PlayerTags
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS
import org.junit.Test

/**
 * Playback control buttons on the player (`PlayerRoute`), exercised against real audio actually playing
 * from the Vault (see [BaseE2ETest] / [LoggedInE2ETest.startPlaybackFromFirstLibraryItem]).
 */
@PlaybackArea
class PlaybackControlsE2ETest : LoggedInE2ETest() {

    private fun tap(description: Int) =
        composeRule.onNodeWithContentDescription(context.getString(description)).performClick()

    @Test
    fun pauseThenResumeKeepsPlaybackOnScreen() {
        loginAndWaitForLibrary()
        startPlaybackFromFirstLibraryItem()

        composeRule.onNodeWithTag(PlayerTags.PLAY_PAUSE).performClick() // pause
        composeRule.onNodeWithTag(PlayerTags.PLAY_PAUSE).performClick() // resume

        composeRule.onNodeWithTag(PlayerTags.PLAY_PAUSE).assertExists()
    }

    @Test
    fun fastForwardAdvancesThePlaybackPosition() {
        loginAndWaitForLibrary()
        startPlaybackFromFirstLibraryItem()
        // A tap while the stream is still opening is dropped
        waitForPlaying()

        val positionBefore = seconds(playerPosition())
        tap(R.string.player_fast_forward) // +30s (audiobook skip-forward interval)

        // Merely "changed" passed by luck when the server transcoded: the clock restarted at 0:00
        // and only differed if it had already ticked past 0:00 before the tap.
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { seconds(playerPosition()) >= positionBefore + MIN_SKIP_SECONDS }
    }

    private fun seconds(position: String): Int =
        position.split(':').fold(0) { total, part -> total * 60 + part.trim().toInt() }

    @Test
    fun chapterSkipButtonsDoNotBreakPlayback() {
        loginAndWaitForLibrary()
        startPlaybackFromFirstLibraryItem()

        // Without chapter markers these fall back to the next/previous queue track
        // (AudiobookPlayerViewModel.nextChapter/previousChapter), and with neither they're hidden: the
        // first book is in several tracks (see VaultFixtures.Audiobooks). Playback should still be
        // running afterward, not crashed.
        waitForPlaying()
        tap(R.string.player_next_chapter)
        composeRule.onNodeWithTag(PlayerTags.PLAY_PAUSE).assertExists()

        tap(R.string.player_previous_chapter)
        composeRule.onNodeWithTag(PlayerTags.PLAY_PAUSE).assertExists()
    }

    private companion object {
        // The skip is 30s; allow for rounding and the position display's update interval
        const val MIN_SKIP_SECONDS = 20
    }
}
