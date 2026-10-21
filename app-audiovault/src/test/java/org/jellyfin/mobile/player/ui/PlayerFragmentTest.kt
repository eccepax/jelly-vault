package org.jellyfin.mobile.player.ui

import android.app.Application
import android.os.Bundle
import android.os.Looper
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.fragment.app.Fragment
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.mobile.common.playback.SleepTimerState
import org.jellyfin.mobile.player.AudiobookPlayerViewModel
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.player.playback.AudiobookTrack
import org.jellyfin.mobile.player.playback.Chapter
import org.jellyfin.mobile.player.playback.FakeAudiobookController
import org.jellyfin.mobile.robolectric.playerViewModel
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.time.Duration
import java.util.UUID
import org.jellyfin.mobile.common.R as CommonR

/**
 * The player screen wired to a real [AudiobookPlayerViewModel] over a fake of the playback
 * service. What the screen itself shows and does is in [PlayerScreenTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PlayerFragmentTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val controller = FakeAudiobookController()
    private val book = AudiobookTrack(
        id = UUID.randomUUID().toString(),
        title = "The Book",
        durationMs = 600_000L,
        chapters = listOf(Chapter(0L, "One"), Chapter(300_000L, "Two")),
    )

    @After
    fun tearDown() {
        stopKoin()
    }

    /** Starts a Koin module around [viewModel] and launches [PlayerFragment] against it. */
    private fun launch(
        viewModel: AudiobookPlayerViewModel = playerViewModel(controller),
        fragmentArgs: Bundle? = null,
        holdToSpeedUp: Boolean = true,
    ): FragmentScenario<PlayerFragment> {
        startKoinWith(viewModel, holdToSpeedUp)
        // No fragmentArgs: opened from the mini player, with the book already playing
        return launchFragmentInContainer<PlayerFragment>(
            fragmentArgs = fragmentArgs,
            themeResId = CommonR.style.AppTheme,
        )
    }

    private fun startKoinWith(viewModel: AudiobookPlayerViewModel, holdToSpeedUp: Boolean = true) {
        startKoin {
            modules(
                module {
                    single { mockk<AppPreferences>(relaxed = true) }
                    single {
                        mockk<AudioVaultAppPreferences>(relaxed = true) {
                            every { exoPlayerAllowPressSpeedUp } returns holdToSpeedUp
                        }
                    }
                    viewModel { viewModel }
                },
            )
        }
    }

    private fun string(resId: Int): String = ApplicationProvider.getApplicationContext<Application>().getString(resId)

    private fun tap(description: Int) = composeRule.onNodeWithContentDescription(string(description)).performClick()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun playOptions() = PlayOptions(
        ids = listOf(UUID.randomUUID()),
        mediaSourceId = null,
        startIndex = 0,
        startPosition = null,
        audioStreamIndex = null,
        subtitleStreamIndex = null,
        playFromDownloads = false,
    )

    @Test
    fun `opened from the mini player, it shows the book playing`() {
        controller.playing(book)

        launch().use {
            idle()
            composeRule.onNodeWithText("The Book").assertExists()
        }
    }

    @Test
    fun `opened from the library, it starts the book`() {
        val options = playOptions()

        launch(fragmentArgs = Bundle().apply { putParcelable(Constants.EXTRA_MEDIA_PLAY_OPTIONS, options) }).use {
            idle()
        }

        controller.commands shouldBe listOf("play ${options.ids.single()} from 0 at 0")
    }

    /**
     * Rotating the screen recreates the Fragment, and its `arguments` (the original [PlayOptions])
     * survive. Replaying them on every rotation jumped the book back to where it started.
     */
    @Test
    fun `recreating the fragment (simulating a screen rotation) does not start the book again`() {
        val fragmentArgs = Bundle().apply { putParcelable(Constants.EXTRA_MEDIA_PLAY_OPTIONS, playOptions()) }

        launch(fragmentArgs = fragmentArgs).use { scenario ->
            idle()
            scenario.recreate()
            idle()
        }

        controller.commands.count { it.startsWith("play ") } shouldBe 1
    }

    @Test
    fun `when the book stops, the screen goes`() {
        controller.playing(book)
        startKoinWith(playerViewModel(controller))

        // Opened over another screen, as the app does, so there's somewhere to go back to
        launchFragmentInContainer<Fragment>(themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { below ->
                below.parentFragmentManager.beginTransaction()
                    .replace(below.id, PlayerFragment())
                    .addToBackStack(null)
                    .commit()
            }
            idle()

            controller.stop()
            idle()

            scenario.onFragment { it.parentFragmentManager.fragments.none { f -> f is PlayerFragment } shouldBe true }
        }
    }

    @Test
    fun `when the book can't be started, it says why and the screen goes`() {
        val viewModel = playerViewModel(controller, items = { throw ApiClientException("No route to host") })
        startKoinWith(viewModel)
        val fragmentArgs = Bundle().apply { putParcelable(Constants.EXTRA_MEDIA_PLAY_OPTIONS, playOptions()) }

        launchFragmentInContainer<Fragment>(themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { below ->
                below.parentFragmentManager.beginTransaction()
                    .replace(below.id, PlayerFragment().apply { arguments = fragmentArgs })
                    .addToBackStack(null)
                    .commit()
            }
            idle()

            ShadowToast.getTextOfLatestToast() shouldBe string(R.string.player_error_network_failure)
            scenario.onFragment { it.parentFragmentManager.fragments.none { f -> f is PlayerFragment } shouldBe true }
        }
    }

    @Test
    fun `pressing play-pause while paused starts playback`() {
        controller.playing(book, isPlaying = false)

        launch().use {
            idle()
            composeRule.onNodeWithTag(PlayerTags.PLAY_PAUSE).performClick()
        }

        controller.commands shouldBe listOf("play")
    }

    @Test
    fun `pressing play-pause while playing pauses playback`() {
        controller.playing(book, isPlaying = true)

        launch().use {
            idle()
            composeRule.onNodeWithTag(PlayerTags.PLAY_PAUSE).performClick()
        }

        controller.commands shouldBe listOf("pause")
    }

    @Test
    fun `the progress keeps updating after leaving and coming back while the book was still loading`() {
        launch().use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED) // left the screen
            scenario.moveToState(Lifecycle.State.RESUMED) // came back, still loading
            controller.playing(book, positionMs = 65_000L) // the book starts

            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

            composeRule.onNodeWithTag(PlayerTags.POSITION).assert(hasText("1:05"))
        }
    }

    @Test
    fun `the skip buttons skip back and forward`() {
        controller.playing(book)

        launch().use {
            idle()
            tap(R.string.player_rewind)
            tap(R.string.player_fast_forward)
        }

        controller.commands shouldBe listOf("rewind", "fastForward")
    }

    @Test
    fun `holding fast-forward plays faster until it's let go`() {
        controller.playing(book)

        launch().use {
            idle()
            composeRule.onNodeWithContentDescription(string(R.string.player_fast_forward))
                .performTouchInput { longClick() }
        }

        controller.commands shouldBe listOf("speed ${Constants.HOLD_SPEEDUP_MULTIPLIER}", "speed 1.0")
    }

    @Test
    fun `with hold to speed up turned off in settings, holding fast-forward leaves the speed alone`() {
        controller.playing(book)

        launch(holdToSpeedUp = false).use {
            idle()
            composeRule.onNodeWithContentDescription(string(R.string.player_fast_forward))
                .performTouchInput { longClick() }
        }

        controller.commands.none { it.startsWith("speed") } shouldBe true
    }

    @Test
    fun `the chapter buttons move between chapters`() {
        controller.playing(book)

        launch().use {
            idle()
            tap(R.string.player_previous_chapter)
            tap(R.string.player_next_chapter)
        }

        controller.commands shouldBe listOf("previousChapter", "nextChapter")
    }

    @Test
    fun `turning off a running sleep timer cancels it and says so`() {
        controller.playing(book)
        val viewModel = playerViewModel(controller)
        viewModel.startSleepTimer(minutes = 15)

        launch(viewModel).use {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            tap(R.string.menu_sleep_timer)
            composeRule.onNodeWithText(string(R.string.sleep_timer_turn_off)).performClick()
        }

        viewModel.sleepTimer.value shouldBe SleepTimerState.Off
        ShadowToast.getTextOfLatestToast() shouldBe string(R.string.sleep_timer_off)
    }

    @Test
    fun `choosing end of chapter starts the timer and says so`() {
        controller.playing(book)
        val viewModel = playerViewModel(controller)

        launch(viewModel).use {
            idle()
            tap(R.string.menu_sleep_timer)
            composeRule.onNodeWithText(string(R.string.sleep_timer_end_of_chapter)).performClick()
        }

        viewModel.sleepTimer.value shouldBe SleepTimerState.EndOfSegment
        ShadowToast.getTextOfLatestToast() shouldBe string(R.string.sleep_timer_set_end_of_chapter)
    }

    @Test
    fun `choosing a speed sets it`() {
        controller.playing(book)

        launch().use {
            idle()
            composeRule.onNodeWithText("1.0x").performClick()
            composeRule.onNodeWithText("1.5x").performClick()
        }

        controller.commands shouldContain "speed 1.5"
    }

    @Test
    fun `while fast-forward is held, the speed button still shows the speed chosen`() {
        controller.playing(book)
        val viewModel = playerViewModel(controller, speed = 1.5f)

        launch(viewModel).use {
            viewModel.holdSpeedUp(true)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

            composeRule.onNodeWithText("1.5x").assertExists()
        }
    }

    @Test
    fun `a playback error is shown`() {
        controller.playing(book)

        launch().use {
            idle()
            controller.state.value = controller.state.value.copy(error = "No route to host")
            idle()
        }

        ShadowToast.getTextOfLatestToast() shouldBe "No route to host"
    }
}
