package org.jellyfin.mobile.music.e2e

import androidx.test.espresso.Espresso.pressBack
import androidx.compose.ui.test.performScrollTo
import org.jellyfin.mobile.music.playback.SleepTimerVisibility
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.jellyfin.mobile.music.nowplaying.MiniPlayerTags
import org.jellyfin.mobile.music.playback.PlaybackController
import org.jellyfin.mobile.music.playback.SleepTimer
import org.jellyfin.mobile.music.playback.SleepTimerState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

private const val PLAYBACK_TIMEOUT_MS = 30_000L

/** The sleep timer against real playback from the Vault. */
@PlaybackArea
class SleepTimerE2ETest : LoggedInE2ETest() {

    private val playback: PlaybackController get() = koinGet()
    private val timer: SleepTimer get() = koinGet()

    @After
    fun timerOffAndShown() = onMain {
        timer.cancel()
        // App-wide, like the timer: leave it as later tests expect.
        koinGet<SleepTimerVisibility>().setShown(true)
    }

    private fun playingOnNowPlaying() {
        connectAndLoginToLibrary()
        openAlbum("The Purple Bird", firstTrack = "Turned To Dust (Rolling On)")
        composeRule.onNodeWithText("London May").performClick()
        composeRule.waitUntil(PLAYBACK_TIMEOUT_MS) { onMain { playback.state.value.isPlaying && playback.positionMs > 1_000L } }
        composeRule.onNodeWithTag(MiniPlayerTags.ROOT).performClick()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            runCatching { composeRule.onNodeWithContentDescription("Collapse").assertExists() }.isSuccess
        }
    }

    @Test
    fun aTimerSetOnNowPlayingCountsDownAndCanBeTurnedOff() {
        playingOnNowPlaying()

        composeRule.onNodeWithContentDescription("Sleep timer").performClick()
        composeRule.onNodeWithText("15 minutes").performClick()

        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeRule.onAllNodes(hasText("Sleeps in 14:5", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Sleep timer").performClick()
        composeRule.onNodeWithText("Turn off").performClick()

        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeRule.onAllNodes(hasText("Sleeps in", substring = true)).fetchSemanticsNodes().isEmpty()
        }
        onMain { assertEquals(SleepTimerState.Off, timer.state.value) }
    }

    @Test
    fun whenTheTimerRunsOutTheMusicPauses() {
        playingOnNowPlaying()

        // Long enough to cover the whole fade, short enough not to keep the suite waiting.
        onMain { timer.start(SleepTimer.FADE_MS + 2_000L) }

        composeRule.waitUntil(SleepTimer.FADE_MS + PLAYBACK_TIMEOUT_MS) { onMain { !playback.state.value.isPlaying } }
        onMain { assertEquals(SleepTimerState.Off, timer.state.value) }
        // Volume is put back for next time: playing again is audible at once.
        composeRule.onNodeWithContentDescription("Play").performClick()
        composeRule.waitUntil(PLAYBACK_TIMEOUT_MS) { onMain { playback.state.value.isPlaying } }
    }

    @Test
    fun hidingTheTimerInSettingsTakesItOffNowPlaying() {
        connectAndLogin()
        waitForComposeText("Recently added")
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Show sleep timer").performScrollTo().performClick()
        pressBack()
        composeRule.onNodeWithText("Library").performClick()
        waitForComposeText("Albums")
        openAlbum("The Purple Bird", firstTrack = "Turned To Dust (Rolling On)")
        composeRule.onNodeWithText("London May").performClick()
        composeRule.waitUntil(PLAYBACK_TIMEOUT_MS) { onMain { playback.state.value.isPlaying } }

        composeRule.onNodeWithTag(MiniPlayerTags.ROOT).performClick()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            runCatching { composeRule.onNodeWithContentDescription("Collapse").assertExists() }.isSuccess
        }

        composeRule.onNodeWithContentDescription("Sleep timer").assertDoesNotExist()
    }
}
