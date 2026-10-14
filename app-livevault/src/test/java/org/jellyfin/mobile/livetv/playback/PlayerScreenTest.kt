package org.jellyfin.mobile.livetv.playback

import android.app.Application
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.livetv.catalog.channel
import org.jellyfin.mobile.livetv.catalog.program
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class PlayerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val bulls = channel("1", "Bulls", now = program("Bulls at Pacers"))
    private val zaps = mutableListOf<Int>()
    private var retried = false
    private var exited = false

    private fun show(playback: PlaybackState, videoStarted: Boolean = false) {
        composeRule.setContent {
            PlayerScreen(
                state = WatchUiState(playback = playback, channel = bulls, videoStarted = videoStarted),
                onZap = { zaps += it },
                onRetry = { retried = true },
                onExit = { exited = true },
                video = {},
            )
        }
    }

    @Test
    fun `tuning shows which channel is coming`() {
        show(PlaybackState.Tuning(bulls.id))

        composeRule.onNodeWithText("Bulls").assertIsDisplayed()
        composeRule.onNodeWithText("1").assertIsDisplayed()
        composeRule.onNodeWithText("Tuning…").assertIsDisplayed()
    }

    @Test
    fun `shows what is on now`() {
        show(PlaybackState.Tuning(bulls.id))

        composeRule.onNodeWithText("Bulls at Pacers").assertIsDisplayed()
    }

    @Test
    fun `waits for the video with a spinner`() {
        show(PlaybackState.Playing(bulls.id), videoStarted = false)

        composeRule.onNodeWithTag(PlayerScreenTags.BUFFERING).assertIsDisplayed()
    }

    @Test
    fun `playing video is marked as playing, with no spinner`() {
        show(PlaybackState.Playing(bulls.id), videoStarted = true)

        composeRule.onNodeWithTag(PlayerScreenTags.VIDEO_PLAYING).assertExists()
        composeRule.onNodeWithTag(PlayerScreenTags.BUFFERING).assertDoesNotExist()
    }

    @Test
    fun `the overlay gets out of the way once the video plays`() {
        show(PlaybackState.Playing(bulls.id), videoStarted = true)
        composeRule.onNodeWithText("Bulls at Pacers").assertExists()

        composeRule.mainClock.advanceTimeBy(OVERLAY_GONE_MS)

        composeRule.onNodeWithText("Bulls at Pacers").assertDoesNotExist()
    }

    @Test
    fun `a failed channel offers retry`() {
        show(PlaybackState.Failed(bulls.id))

        composeRule.onNodeWithText("Couldn't play this channel").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()

        retried shouldBe true
    }

    @Test
    fun `back leaves the player`() {
        show(PlaybackState.Tuning(bulls.id))

        composeRule.onNodeWithContentDescription("Back to channels").performClick()

        exited shouldBe true
    }

    @Test
    fun `channel buttons zap up and down`() {
        show(PlaybackState.Tuning(bulls.id))

        composeRule.onNodeWithContentDescription("Next channel").performClick()
        composeRule.onNodeWithContentDescription("Previous channel").performClick()

        zaps shouldContainExactly listOf(+1, -1)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the remote's up and down change channel`() {
        show(PlaybackState.Playing(bulls.id), videoStarted = true)

        composeRule.onRoot().performKeyInput {
            pressKey(Key.DirectionUp)
            pressKey(Key.DirectionDown)
            pressKey(Key.ChannelUp)
            pressKey(Key.ChannelDown)
        }

        zaps shouldContainExactly listOf(+1, -1, +1, -1)
    }
}

private const val OVERLAY_GONE_MS = 5_000L
