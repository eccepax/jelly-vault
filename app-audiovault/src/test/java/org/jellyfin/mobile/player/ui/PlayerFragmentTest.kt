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
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.mobile.player.AudiobookPlayerViewModel
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.robolectric.mockedPlayerViewModelForPlayerFragment
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.time.Duration
import java.util.UUID
import org.jellyfin.mobile.common.R as CommonR

/**
 * The player screen wired to [AudiobookPlayerViewModel], mocked through
 * [mockedPlayerViewModelForPlayerFragment], so no real ExoPlayer, MediaSession or sleep timer is
 * built. What the screen itself shows and does is in [PlayerScreenTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PlayerFragmentTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val apiClient = mockk<ApiClient>(relaxed = true)

    @Before
    fun setUp() {
        // The screen asks for the cover; apiClient.imageApi is an extension with real logic
        mockkStatic(API_CLIENT_EXTENSIONS)
        every { apiClient.imageApi } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkStatic(API_CLIENT_EXTENSIONS)
    }

    /** Starts a scoped Koin test module around [viewModel] and launches [PlayerFragment] against it. */
    private fun launch(
        viewModel: AudiobookPlayerViewModel,
        fragmentArgs: Bundle? = null,
        holdToSpeedUp: Boolean = true,
    ): FragmentScenario<PlayerFragment> {
        startKoin {
            modules(
                module {
                    single { apiClient }
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
        // No fragmentArgs: the "ResumePlayer" path (tap the mini player while audio is already
        // playing) — the ViewModel already has an active player, so no PlayOptions are needed.
        return launchFragmentInContainer<PlayerFragment>(
            fragmentArgs = fragmentArgs,
            themeResId = CommonR.style.AppTheme,
        )
    }

    private fun string(resId: Int): String = ApplicationProvider.getApplicationContext<Application>().getString(resId)

    private fun tap(description: Int) = composeRule.onNodeWithContentDescription(string(description)).performClick()

    @Test
    fun `the player screen launches without crashing when reconnecting to an active session`() {
        launch(mockedPlayerViewModelForPlayerFragment()).use {
            composeRule.onNodeWithTag(PlayerTags.PLAY_PAUSE).assertExists()
        }
    }

    /**
     * Rotating the screen recreates the Fragment, and its `arguments` (the original [PlayOptions])
     * survive. Without the `savedInstanceState != null` guard in `onCreate`, those stale options were
     * replayed on every rotation and the chapter jumped back to where playback started.
     */
    @Test
    fun `recreating the fragment (simulating a screen rotation) does not replay the original PlayOptions`() {
        val playerViewModel = mockedPlayerViewModelForPlayerFragment()
        val playOptions = PlayOptions(
            ids = listOf(UUID.randomUUID()),
            mediaSourceId = null,
            startIndex = 0,
            startPosition = null,
            audioStreamIndex = null,
            subtitleStreamIndex = null,
            playFromDownloads = false,
        )
        val fragmentArgs = Bundle().apply { putParcelable(Constants.EXTRA_MEDIA_PLAY_OPTIONS, playOptions) }
        // Captured once: chaining through playerViewModel.queueManager in coVerify confuses MockK
        val queueManager = playerViewModel.queueManager

        launch(playerViewModel, fragmentArgs = fragmentArgs).use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            coVerify(exactly = 1) { queueManager.initializePlaybackQueue(playOptions) }

            scenario.recreate()
            shadowOf(Looper.getMainLooper()).idle()

            coVerify(exactly = 1) { queueManager.initializePlaybackQueue(playOptions) }
        }
    }

    @Test
    fun `pressing play-pause while paused starts playback`() {
        val playerViewModel = mockedPlayerViewModelForPlayerFragment(playWhenReady = false)

        launch(playerViewModel).use { composeRule.onNodeWithTag(PlayerTags.PLAY_PAUSE).performClick() }

        verify { playerViewModel.play() }
        verify(exactly = 0) { playerViewModel.pause() }
    }

    @Test
    fun `pressing play-pause while playing pauses playback`() {
        val playerViewModel = mockedPlayerViewModelForPlayerFragment(playWhenReady = true)

        launch(playerViewModel).use { composeRule.onNodeWithTag(PlayerTags.PLAY_PAUSE).performClick() }

        verify { playerViewModel.pause() }
        verify(exactly = 0) { playerViewModel.play() }
    }

    @Test
    fun `the progress keeps updating after leaving and coming back while the book was still loading`() {
        val playerViewModel = mockedPlayerViewModelForPlayerFragment()
        val player = requireNotNull(playerViewModel.playerOrNull)
        every { playerViewModel.playerOrNull } returns null // still loading

        launch(playerViewModel).use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED) // left the screen
            scenario.moveToState(Lifecycle.State.RESUMED) // came back, still loading
            every { playerViewModel.playerOrNull } returns player // the book starts
            every { playerViewModel.adjustedCurrentPosition } returns 65_000L
            every { player.duration } returns 600_000L

            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

            composeRule.onNodeWithTag(PlayerTags.POSITION).assert(hasText("1:05"))
        }
    }

    @Test
    fun `the skip buttons skip back and forward`() {
        val playerViewModel = mockedPlayerViewModelForPlayerFragment()

        launch(playerViewModel).use {
            tap(R.string.player_rewind)
            tap(R.string.player_fast_forward)
        }

        verify { playerViewModel.rewind() }
        verify { playerViewModel.fastForward() }
        verify(exactly = 0) { playerViewModel.setPressSpeedUp(any(), any()) }
    }

    @Test
    fun `holding fast-forward plays faster until it's let go`() {
        val playerViewModel = mockedPlayerViewModelForPlayerFragment()

        launch(playerViewModel).use {
            composeRule.onNodeWithContentDescription(string(R.string.player_fast_forward))
                .performTouchInput { longClick() }
        }

        verify { playerViewModel.setPressSpeedUp(true, Constants.HOLD_SPEEDUP_MULTIPLIER) }
        verify { playerViewModel.setPressSpeedUp(false, Constants.HOLD_SPEEDUP_MULTIPLIER) }
        verify(exactly = 0) { playerViewModel.fastForward() }
    }

    @Test
    fun `with hold to speed up turned off in settings, holding fast-forward leaves the speed alone`() {
        val playerViewModel = mockedPlayerViewModelForPlayerFragment()

        launch(playerViewModel, holdToSpeedUp = false).use {
            composeRule.onNodeWithContentDescription(string(R.string.player_fast_forward))
                .performTouchInput { longClick() }
        }

        verify(exactly = 0) { playerViewModel.setPressSpeedUp(any(), any()) }
    }

    @Test
    fun `the chapter buttons move between chapters`() {
        val playerViewModel = mockedPlayerViewModelForPlayerFragment()
        val queueManager = playerViewModel.queueManager
        every { queueManager.hasNext() } returns true

        launch(playerViewModel).use {
            (queueManager.currentMediaSource as MutableLiveData).value = mockk<JellyfinMediaSource>(relaxed = true)
            tap(R.string.player_previous_chapter)
            tap(R.string.player_next_chapter)
        }

        verify { playerViewModel.previousChapter() }
        verify { playerViewModel.nextChapter() }
    }

    @Test
    fun `turning off a running sleep timer cancels it and says so`() {
        val viewModel = mockedPlayerViewModelForPlayerFragment()
        every { viewModel.isSleepTimerActive } returns true

        launch(viewModel).use {
            tap(R.string.menu_sleep_timer)
            composeRule.onNodeWithText(string(R.string.sleep_timer_turn_off)).performClick()
        }

        verify { viewModel.cancelSleepTimer() }
        ShadowToast.getTextOfLatestToast() shouldBe string(R.string.sleep_timer_off)
    }

    @Test
    fun `choosing end of chapter starts the timer and says so`() {
        val viewModel = mockedPlayerViewModelForPlayerFragment()

        launch(viewModel).use {
            tap(R.string.menu_sleep_timer)
            composeRule.onNodeWithText(string(R.string.sleep_timer_end_of_chapter)).performClick()
        }

        verify { viewModel.startSleepTimerEndOfChapter() }
        ShadowToast.getTextOfLatestToast() shouldBe string(R.string.sleep_timer_set_end_of_chapter)
    }

    @Test
    fun `choosing a speed sets it`() {
        val viewModel = mockedPlayerViewModelForPlayerFragment()

        launch(viewModel).use {
            composeRule.onNodeWithText("1.0x").performClick()
            composeRule.onNodeWithText("1.5x").performClick()
        }

        verify { viewModel.setPlaybackSpeed(1.5f) }
    }

    private companion object {
        const val API_CLIENT_EXTENSIONS = "org.jellyfin.sdk.api.client.extensions.ApiClientExtensionsKt"
    }
}
