package org.jellyfin.mobile.player.ui

import android.app.Application
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.children
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.R
import org.jellyfin.mobile.common.playback.SleepTimerState
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.mobile.player.AudiobookPlayerViewModel
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.player.playback.AudiobookTrack
import org.jellyfin.mobile.player.playback.Chapter
import org.jellyfin.mobile.player.playback.FakeAudiobookController
import org.jellyfin.mobile.robolectric.playerViewModel
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.util.UUID
import org.jellyfin.mobile.common.R as CommonR

/**
 * The player screen over a real [AudiobookPlayerViewModel] and a fake of the playback service.
 * What the screen itself shows and does is in [PlayerScreenTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PlayerRouteTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val controller = FakeAudiobookController()
    private val book = AudiobookTrack(
        id = UUID.randomUUID().toString(),
        title = "The Book",
        durationMs = 600_000L,
        chapters = listOf(Chapter(0L, "One"), Chapter(300_000L, "Two")),
    )
    private var backs = 0

    /** Whether the player screen is showing: tests take it away, as opening another screen over it does. */
    private var showing by mutableStateOf(true)

    private fun show(viewModel: AudiobookPlayerViewModel = playerViewModel(controller)): AudiobookPlayerViewModel {
        composeRule.setContent {
            AppTheme(VaultBrand.AudioVault) {
                if (showing) PlayerRoute(onBack = { backs++ }, viewModel = viewModel)
            }
        }
        idle()
        return viewModel
    }

    private fun string(resId: Int): String = ApplicationProvider.getApplicationContext<Application>().getString(resId)

    private fun tap(description: Int) = composeRule.onNodeWithContentDescription(string(description)).performClick()

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
    }

    private fun playOptions() = PlayOptions(
        ids = listOf(UUID.randomUUID()),
        mediaSourceId = null,
        startIndex = 0,
        startPosition = null,
        audioStreamIndex = null,
        subtitleStreamIndex = null,
        playFromDownloads = false,
    )

    private fun unreachable() = playerViewModel(controller, items = { throw ApiClientException("No route to host") })

    @Test
    fun `it shows the book playing`() {
        controller.playing(book)

        show()

        composeRule.onNodeWithText("The Book").assertExists()
    }

    @Test
    fun `back goes back`() {
        controller.playing(book)
        show()

        tap(CommonR.string.vault_back)

        backs shouldBe 1
    }

    @Test
    fun `when the book stops, the screen goes`() {
        controller.playing(book)
        show()

        controller.stop()
        idle()

        backs shouldBe 1
    }

    @Test
    fun `when the book can't be started, it says why and the screen goes`() {
        val viewModel = show(unreachable())

        viewModel.play(playOptions())
        idle()

        ShadowToast.getTextOfLatestToast() shouldBe string(R.string.player_error_network_failure)
        backs shouldBe 1
    }

    /** As when another screen was opened over it, or the app went to the background, while the book loaded. */
    @Test
    fun `when the book can't be started while the screen isn't showing, it goes once it's back`() {
        val viewModel = unreachable()
        showing = false
        show(viewModel)

        viewModel.play(playOptions())
        idle()
        backs shouldBe 0

        showing = true
        idle()

        ShadowToast.getTextOfLatestToast() shouldBe string(R.string.player_error_network_failure)
        backs shouldBe 1
    }

    @Test
    fun `when another book can't be started, the one playing stays on screen`() {
        controller.playing(book)
        val viewModel = show(unreachable())

        viewModel.play(playOptions())
        idle()

        ShadowToast.getTextOfLatestToast() shouldBe string(R.string.player_error_network_failure)
        backs shouldBe 0
    }

    @Test
    fun `pressing play-pause while paused starts playback`() {
        controller.playing(book, isPlaying = false)
        show()

        composeRule.onNodeWithTag(PlayerTags.PLAY_PAUSE).performClick()

        controller.commands shouldBe listOf("play")
    }

    @Test
    fun `pressing play-pause while playing pauses playback`() {
        controller.playing(book, isPlaying = true)
        show()

        composeRule.onNodeWithTag(PlayerTags.PLAY_PAUSE).performClick()

        controller.commands shouldBe listOf("pause")
    }

    @Test
    fun `the position keeps moving with the book, once it has started`() {
        show()

        controller.playing(book, positionMs = 65_000L)
        composeRule.mainClock.advanceTimeBy(1_000L)

        composeRule.onNodeWithTag(PlayerTags.POSITION).assert(hasText("1:05"))
    }

    @Test
    fun `the screen stays on while the book plays, and not once it's paused`() {
        controller.playing(book, isPlaying = true)
        show()
        keepsScreenOn() shouldBe true

        controller.playing(book, isPlaying = false)
        idle()

        keepsScreenOn() shouldBe false
    }

    private fun keepsScreenOn(): Boolean = composeRule.activity.window.decorView.anyKeepsScreenOn()

    private fun View.anyKeepsScreenOn(): Boolean =
        keepScreenOn || (this is ViewGroup && children.any { it.anyKeepsScreenOn() })

    @Test
    fun `the skip buttons skip back and forward`() {
        controller.playing(book)
        show()

        tap(R.string.player_rewind)
        tap(R.string.player_fast_forward)

        controller.commands shouldBe listOf("rewind", "fastForward")
    }

    @Test
    fun `holding fast-forward plays faster until it's let go`() {
        controller.playing(book)
        show()

        composeRule.onNodeWithContentDescription(string(R.string.player_fast_forward)).performTouchInput { longClick() }

        controller.commands shouldBe listOf("speed ${Constants.HOLD_SPEEDUP_MULTIPLIER}", "speed 1.0")
    }

    @Test
    fun `with hold to speed up turned off in settings, holding fast-forward leaves the speed alone`() {
        controller.playing(book)
        show(playerViewModel(controller, holdToSpeedUp = false))

        composeRule.onNodeWithContentDescription(string(R.string.player_fast_forward)).performTouchInput { longClick() }

        controller.commands.none { it.startsWith("speed") } shouldBe true
    }

    @Test
    fun `the chapter buttons move between chapters`() {
        controller.playing(book)
        show()

        tap(R.string.player_previous_chapter)
        tap(R.string.player_next_chapter)

        controller.commands shouldBe listOf("previousChapter", "nextChapter")
    }

    @Test
    fun `turning off a running sleep timer cancels it and says so`() {
        controller.playing(book)
        val viewModel = playerViewModel(controller)
        viewModel.startSleepTimer(minutes = 15)
        show(viewModel)

        tap(R.string.menu_sleep_timer)
        composeRule.onNodeWithText(string(R.string.sleep_timer_turn_off)).performClick()

        viewModel.sleepTimer.value shouldBe SleepTimerState.Off
        ShadowToast.getTextOfLatestToast() shouldBe string(R.string.sleep_timer_off)
    }

    @Test
    fun `choosing end of chapter starts the timer and says so`() {
        controller.playing(book)
        val viewModel = show()

        tap(R.string.menu_sleep_timer)
        composeRule.onNodeWithText(string(R.string.sleep_timer_end_of_chapter)).performClick()

        viewModel.sleepTimer.value shouldBe SleepTimerState.EndOfSegment
        ShadowToast.getTextOfLatestToast() shouldBe string(R.string.sleep_timer_set_end_of_chapter)
    }

    @Test
    fun `choosing a speed sets it`() {
        controller.playing(book)
        show()

        composeRule.onNodeWithText("1.0x").performClick()
        composeRule.onNodeWithText("1.5x").performClick()

        controller.commands shouldContain "speed 1.5"
    }

    @Test
    fun `while fast-forward is held, the speed button still shows the speed chosen`() {
        controller.playing(book)
        val viewModel = show(playerViewModel(controller, speed = 1.5f))

        viewModel.holdSpeedUp(true)
        idle()

        composeRule.onNodeWithText("1.5x").assertExists()
    }

    @Test
    fun `a playback error is shown`() {
        controller.playing(book)
        show()

        controller.state.value = controller.state.value.copy(error = "No route to host")
        idle()

        ShadowToast.getTextOfLatestToast() shouldBe "No route to host"
    }
}
