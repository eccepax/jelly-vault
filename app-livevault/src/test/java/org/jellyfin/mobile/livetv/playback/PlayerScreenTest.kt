package org.jellyfin.mobile.livetv.playback

import android.app.Application
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
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
    private var showedFree = false
    private var castingStopped = false

    private fun show(
        playback: PlaybackState,
        videoStarted: Boolean = false,
        loginInUse: LoginInUse? = null,
        inPictureInPicture: Boolean = false,
        castDevice: String? = null,
    ) {
        composeRule.setContent {
            PlayerScreen(
                state = WatchUiState(
                    playback = playback,
                    channel = bulls,
                    videoStarted = videoStarted,
                    loginInUse = loginInUse,
                    castDevice = castDevice,
                ),
                onZap = { zaps += it },
                onRetry = { retried = true },
                onExit = { exited = true },
                onShowFree = { showedFree = true },
                video = { Text("video", Modifier.testTag(VIDEO)) },
                inPictureInPicture = inPictureInPicture,
                onStopCasting = { castingStopped = true },
                castButton = { Text("cast", Modifier.testTag(CAST_BUTTON)) },
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
    fun `a failed free channel does not mention the login`() {
        show(PlaybackState.Failed(bulls.id))

        composeRule.onNodeWithText("Your TV provider's login is in use").assertDoesNotExist()
        composeRule.onNodeWithText("Free channels").assertDoesNotExist()
    }

    @Test
    fun `a busy login is explained, with free channels offered`() {
        show(PlaybackState.Failed(bulls.id), loginInUse = LoginInUse(holders = emptyList()))

        composeRule.onNodeWithText("Your TV provider's login is in use").assertIsDisplayed()
        composeRule.onNodeWithText("It allows one stream at a time. Free channels still work.").assertIsDisplayed()
        composeRule.onNodeWithText("Free channels").performClick()

        showedFree shouldBe true
    }

    @Test
    fun `a busy login names who is watching`() {
        val roku = ChannelSession(deviceName = "Living Room", userName = "root", channelName = "ESPN")
        show(PlaybackState.Failed(bulls.id), loginInUse = LoginInUse(holders = listOf(roku)))

        composeRule.onNodeWithText("Living Room (root) is watching ESPN").assertIsDisplayed()
    }

    @Test
    fun `a busy login can still be retried`() {
        show(PlaybackState.Failed(bulls.id), loginInUse = LoginInUse(holders = emptyList()))

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

    @Test
    fun `the cast button is in the top bar`() {
        show(PlaybackState.Tuning(bulls.id))

        composeRule.onNodeWithTag(CAST_BUTTON, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `while casting it says where the channel plays`() {
        show(PlaybackState.Playing(bulls.id), videoStarted = true, castDevice = "Main Bedroom TV")

        composeRule.onNodeWithText("Playing on Main Bedroom TV").assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerScreenTags.CASTING, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `while casting the channel's details stay up, with no video here to look at`() {
        show(PlaybackState.Playing(bulls.id), videoStarted = true, castDevice = "Main Bedroom TV")

        composeRule.mainClock.advanceTimeBy(OVERLAY_GONE_MS)

        composeRule.onNodeWithText("Bulls at Pacers").assertIsDisplayed()
    }

    @Test
    fun `tapping where it plays offers to stop casting`() {
        show(PlaybackState.Playing(bulls.id), videoStarted = true, castDevice = "Main Bedroom TV")

        composeRule.onNodeWithText("Playing on Main Bedroom TV").performClick()
        composeRule.onNodeWithText("Stop casting").performClick()

        castingStopped shouldBe true
    }

    @Test
    fun `not casting, nothing says it is`() {
        show(PlaybackState.Playing(bulls.id), videoStarted = true)

        composeRule.onNodeWithTag(PlayerScreenTags.CASTING).assertDoesNotExist()
    }

    @Test
    fun `in the floating window there's only the video`() {
        show(PlaybackState.Playing(bulls.id), videoStarted = true, inPictureInPicture = true)

        composeRule.onNodeWithTag(VIDEO).assertIsDisplayed()
        composeRule.onNodeWithText("Bulls").assertDoesNotExist()
        composeRule.onNodeWithText("Bulls at Pacers").assertDoesNotExist()
    }

    @Test
    fun `in the floating window, a failure still says so`() {
        show(PlaybackState.Failed(bulls.id), inPictureInPicture = true)

        composeRule.onNodeWithText("Bulls").assertDoesNotExist()
        composeRule.onNodeWithTag(PlayerScreenTags.FAILED_SMALL).assertIsDisplayed()
    }

    @Test
    fun `going into the floating window and back keeps the same video, without a black flash`() {
        var videosMade = 0
        var inPictureInPicture by mutableStateOf(false)
        composeRule.setContent {
            PlayerScreen(
                state = WatchUiState(PlaybackState.Playing(bulls.id), bulls, videoStarted = true),
                onZap = {},
                onRetry = {},
                onExit = {},
                onShowFree = {},
                // Stands in for the PlayerView, which re-attaching to a new surface makes flicker
                video = {
                    remember { videosMade++ }
                    Text("video", Modifier.testTag(VIDEO))
                },
                inPictureInPicture = inPictureInPicture,
            )
        }

        inPictureInPicture = true
        composeRule.waitForIdle()
        inPictureInPicture = false
        composeRule.waitForIdle()

        videosMade shouldBe 1
    }
}

private const val OVERLAY_GONE_MS = 5_000L
private const val VIDEO = "video"
private const val CAST_BUTTON = "cast_button"
