package org.jellyfin.mobile.player.ui

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.R
import org.jellyfin.mobile.common.ui.VaultTestTags
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.mobile.player.sleeptimer.SleepTimerManager.SleepTimerDuration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.jellyfin.mobile.common.R as CommonR

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PlayerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()

    private val actions = PlayerActions(
        onBack = { events += "back" },
        onPlayPause = { events += "playPause" },
        onRewind = { events += "rewind" },
        onFastForward = { events += "fastForward" },
        onHoldSpeedUp = { events += "hold $it" },
        onPreviousChapter = { events += "previousChapter" },
        onNextChapter = { events += "nextChapter" },
        onSeek = { events += "seek $it" },
        onSpeed = { events += "speed $it" },
        onSleepTimer = { events += "sleepTimer $it" },
        onTrack = { events += "track $it" },
        onSkipSegment = { events += "skipSegment" },
    )

    private val playing = PlayerUiState(
        title = "Chapter 3",
        isPlaying = true,
        positionMs = 65_000,
        durationMs = 3_600_000,
        speed = 1f,
        holdToSpeedUp = true,
    )

    private fun show(state: PlayerUiState = playing) {
        composeRule.setContent { AppTheme(VaultBrand.AudioVault) { PlayerScreen(state, actions) } }
    }

    private fun string(resId: Int) = ApplicationProvider.getApplicationContext<Application>().getString(resId)

    private fun openMenu() =
        composeRule.onNodeWithContentDescription(string(CommonR.string.vault_more_options)).performClick()

    @Test
    fun `it shows what's playing, where it is and how long it is`() {
        show()

        composeRule.onNodeWithText("Chapter 3").assertExists()
        composeRule.onNodeWithTag(PlayerTags.POSITION).assert(hasText("1:05"))
        composeRule.onNodeWithTag(PlayerTags.DURATION).assert(hasText("1:00:00"))
    }

    @Test
    fun `with no length known yet, it says so`() {
        show(playing.copy(durationMs = 0))

        composeRule.onNodeWithTag(PlayerTags.DURATION).assert(hasText("--:--"))
    }

    @Test
    fun `play and pause say what they do, and do it`() {
        show()

        composeRule.onNodeWithContentDescription(string(CommonR.string.vault_pause)).performClick()

        events shouldBe listOf("playPause")
    }

    @Test
    fun `the skip buttons go back 10 seconds and forward 30`() {
        show()

        composeRule.onNodeWithContentDescription(string(R.string.player_rewind)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.player_fast_forward)).performClick()

        events shouldBe listOf("rewind", "fastForward")
    }

    @Test
    fun `holding fast-forward plays faster until it's let go, without skipping`() {
        show()

        composeRule.onNodeWithContentDescription(string(R.string.player_fast_forward)).performTouchInput { longClick() }

        events shouldBe listOf("hold true", "hold false")
    }

    @Test
    fun `with hold to speed up off, holding fast-forward just skips`() {
        show(playing.copy(holdToSpeedUp = false))

        composeRule.onNodeWithContentDescription(string(R.string.player_fast_forward)).performTouchInput { longClick() }

        events shouldBe listOf("fastForward")
    }

    @Test
    fun `the chapter buttons go to the chapter either side`() {
        show(playing.copy(showChapterButtons = true))

        composeRule.onNodeWithContentDescription(string(R.string.player_previous_chapter)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.player_next_chapter)).performClick()

        events shouldBe listOf("previousChapter", "nextChapter")
    }

    @Test
    fun `with no chapters and one track, there are no chapter buttons`() {
        show()

        composeRule.onNodeWithContentDescription(string(R.string.player_next_chapter)).assertDoesNotExist()
    }

    @Test
    fun `moving the seek bar seeks to that point`() {
        show()

        composeRule.onNodeWithTag(PlayerTags.SEEK_BAR).performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }

        events.last() shouldBe "seek 1800000"
    }

    @Test
    fun `the speed shows, and choosing another sets it`() {
        show(playing.copy(speed = 1.5f))

        composeRule.onNodeWithText("1.5x").performClick()
        composeRule.onNode(hasText("1.5x") and isSelectable()).assertIsSelected()
        composeRule.onNode(hasText("1.0x") and isSelectable()).assertIsNotSelected()
        composeRule.onNodeWithText("2.0x").performClick()

        events shouldBe listOf("speed 2.0")
    }

    @Test
    fun `the sleep timer offers each length, and only offers turning off while one runs`() {
        show()

        composeRule.onNodeWithContentDescription(string(R.string.menu_sleep_timer)).performClick()
        composeRule.onNodeWithText(string(R.string.sleep_timer_turn_off)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.sleep_timer_15_min)).performClick()

        events shouldBe listOf("sleepTimer ${SleepTimerChoice.For(SleepTimerDuration.FIFTEEN_MINUTES)}")
    }

    @Test
    fun `a running sleep timer can be turned off`() {
        show(playing.copy(sleepTimerActive = true))

        composeRule.onNodeWithContentDescription(string(R.string.menu_sleep_timer)).performClick()
        composeRule.onNodeWithText(string(R.string.sleep_timer_turn_off)).performClick()

        events shouldBe listOf("sleepTimer ${SleepTimerChoice.TurnOff}")
    }

    @Test
    fun `the tracklist marks the track playing, and choosing one plays it`() {
        show(playing.copy(tracks = listOf("Part 1", "Part 2", "Part 3"), currentTrack = 1))

        openMenu()
        composeRule.onNodeWithText(string(R.string.player_tracklist)).performClick()
        composeRule.onNode(hasText("Part 2") and isSelectable()).assertIsSelected()
        composeRule.onNodeWithText("Part 3").performClick()

        events shouldBe listOf("track 2")
    }

    @Test
    fun `with one track, there's no tracklist`() {
        show(playing.copy(tracks = listOf("Part 1")))

        openMenu()
        composeRule.onNodeWithText(string(R.string.player_tracklist)).assertDoesNotExist()
    }

    @Test
    fun `playback info shows and hides`() {
        show(playing.copy(playbackInfo = "Play method: DirectPlay"))

        openMenu()
        composeRule.onNodeWithText(string(R.string.menu_playback_info)).performClick()
        composeRule.onNodeWithText("Play method: DirectPlay").assertExists().performClick()

        composeRule.onNodeWithText("Play method: DirectPlay").assertDoesNotExist()
    }

    @Test
    fun `in a skippable segment, skip skips it`() {
        show(playing.copy(skipSegment = true))

        composeRule.onNodeWithText(string(R.string.skip_media_segment)).performClick()

        events shouldBe listOf("skipSegment")
    }

    @Test
    fun `while buffering, it shows progress`() {
        show(playing.copy(buffering = true))

        composeRule.onNodeWithTag(VaultTestTags.LOADING).assertExists()
    }

    @Test
    fun `back leaves the player`() {
        show()

        composeRule.onNodeWithContentDescription(string(CommonR.string.vault_back)).performClick()

        events shouldBe listOf("back")
    }
}
