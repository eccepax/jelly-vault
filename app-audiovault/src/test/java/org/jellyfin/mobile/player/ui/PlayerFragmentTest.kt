package org.jellyfin.mobile.player.ui

import android.app.Application
import android.os.Bundle
import android.os.Looper
import android.view.Menu
import android.view.View
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jellyfin.mobile.R
import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.mobile.player.AudiobookPlayerViewModel
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.robolectric.mockedPlayerViewModelForPlayerFragment
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.sdk.api.client.ApiClient
import org.junit.After
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
import java.util.UUID

/**
 * Tests [PlayerFragment] with [AudiobookPlayerViewModel] mocked through
 * [mockedPlayerViewModelForPlayerFragment], so no real ExoPlayer, MediaSession or sleep timer is built.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PlayerFragmentTest {

    @After
    fun tearDown() {
        stopKoin()
    }

    /**
     * PlayerView internally re-inflates exo_player_control_view.xml, giving its (GONE) copy the
     * same `player_controls`/button ids as the app's own real controls — see the comment on
     * PlayerFragment.onCreateView. A root-level `findViewById` finds that dead copy first, so
     * button lookups must be scoped through `main_content` exactly like the production code does.
     */
    private fun PlayerFragment.controlButton(id: Int): View =
        requireView().findViewById<View>(R.id.main_content).findViewById(id)

    /** Starts a scoped Koin test module around [viewModel] and launches [PlayerFragment] against it. */
    private fun launch(
        viewModel: AudiobookPlayerViewModel,
        fragmentArgs: Bundle? = null,
    ): FragmentScenario<PlayerFragment> {
        startKoin {
            modules(
                module {
                    single { mockk<ApiClient>(relaxed = true) }
                    single { mockk<AppPreferences>(relaxed = true) }
                    viewModel { viewModel }
                },
            )
        }
        // No fragmentArgs: the "ResumePlayer" path (tap the mini player while audio is already
        // playing) — the ViewModel already has an active player, so no PlayOptions are needed.
        return launchFragmentInContainer<PlayerFragment>(fragmentArgs = fragmentArgs, themeResId = R.style.AppTheme)
    }

    @Test
    fun `the player screen launches without crashing when reconnecting to an active session`() {
        launch(mockedPlayerViewModelForPlayerFragment()).use { scenario ->
            scenario.onFragment { fragment -> requireNotNull(fragment.view) }
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
        // Capture the queueManager mock once and verify directly on it — chaining through
        // playerViewModel.queueManager.x() in coVerify confuses MockK's call matching once
        // getQueueManager() has also been invoked elsewhere (e.g. observeCurrentMediaSource()).
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

        launch(playerViewModel).use { scenario ->
            scenario.onFragment { fragment ->
                fragment.controlButton(R.id.exo_play_pause).performClick()
            }
        }

        verify { playerViewModel.play() }
        verify(exactly = 0) { playerViewModel.pause() }
    }

    @Test
    fun `pressing play-pause while playing pauses playback`() {
        val playerViewModel = mockedPlayerViewModelForPlayerFragment(playWhenReady = true)

        launch(playerViewModel).use { scenario ->
            scenario.onFragment { fragment ->
                fragment.controlButton(R.id.exo_play_pause).performClick()
            }
        }

        verify { playerViewModel.pause() }
        verify(exactly = 0) { playerViewModel.play() }
    }

    @Test
    fun `the rewind button skips backward`() {
        val playerViewModel = mockedPlayerViewModelForPlayerFragment()

        launch(playerViewModel).use { scenario ->
            scenario.onFragment { fragment ->
                fragment.controlButton(R.id.previous_button).performClick()
            }
        }

        verify { playerViewModel.rewind() }
    }

    @Test
    fun `the fast-forward button skips forward`() {
        val playerViewModel = mockedPlayerViewModelForPlayerFragment()

        launch(playerViewModel).use { scenario ->
            scenario.onFragment { fragment ->
                fragment.controlButton(R.id.next_button).performClick()
            }
        }

        verify { playerViewModel.fastForward() }
    }

    @Test
    fun `the previous-chapter button navigates to the previous chapter`() {
        val playerViewModel = mockedPlayerViewModelForPlayerFragment()

        launch(playerViewModel).use { scenario ->
            scenario.onFragment { fragment ->
                fragment.controlButton(R.id.previous_chapter_button).performClick()
            }
        }

        verify { playerViewModel.previousChapter() }
    }

    @Test
    fun `the next-chapter button navigates to the next chapter`() {
        val playerViewModel = mockedPlayerViewModelForPlayerFragment()

        launch(playerViewModel).use { scenario ->
            scenario.onFragment { fragment ->
                fragment.controlButton(R.id.next_chapter_button).performClick()
            }
        }

        verify { playerViewModel.nextChapter() }
    }

    private fun PlayerFragment.overflowMenu(): Menu =
        requireView().findViewById<Toolbar>(R.id.toolbar).menu

    @Test
    fun `the sleep timer overflow action shows the sleep timer dialog`() {
        launch(mockedPlayerViewModelForPlayerFragment()).use { scenario ->
            scenario.onFragment { fragment ->
                fragment.overflowMenu().performIdentifierAction(R.id.action_sleep_timer, 0)
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onFragment { fragment ->
                fragment.parentFragmentManager.findFragmentByTag("SleepTimerDialog") shouldNotBe null
            }
        }
    }

    /** Opens the sleep timer dialog from the overflow menu and returns its option list. */
    private fun FragmentScenario<PlayerFragment>.openSleepTimerDialog(): ListView {
        onFragment { it.overflowMenu().performIdentifierAction(R.id.action_sleep_timer, 0) }
        shadowOf(Looper.getMainLooper()).idle()
        var options: ListView? = null
        onFragment { fragment ->
            val dialog = fragment.parentFragmentManager.findFragmentByTag("SleepTimerDialog") as DialogFragment
            options = (dialog.requireDialog() as AlertDialog).listView
        }
        return requireNotNull(options)
    }

    private fun ListView.optionLabels(): List<String> = (0 until adapter.count).map { adapter.getItem(it).toString() }

    private fun ListView.choose(label: String) {
        val index = optionLabels().indexOf(label)
        check(index >= 0) { "No sleep timer option \"$label\" in ${optionLabels()}" }
        performItemClick(null, index, index.toLong())
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun string(resId: Int): String = ApplicationProvider.getApplicationContext<Application>().getString(resId)

    @Test
    fun `the sleep timer dialog has no turn-off option while no timer is running`() {
        val viewModel = mockedPlayerViewModelForPlayerFragment()
        every { viewModel.isSleepTimerActive } returns false
        launch(viewModel).use { scenario ->
            scenario.openSleepTimerDialog().optionLabels() shouldNotContain string(R.string.sleep_timer_turn_off)
        }
    }

    @Test
    fun `turning off a running sleep timer cancels it`() {
        val viewModel = mockedPlayerViewModelForPlayerFragment()
        every { viewModel.isSleepTimerActive } returns true
        launch(viewModel).use { scenario ->
            scenario.openSleepTimerDialog().choose(string(R.string.sleep_timer_turn_off))

            verify { viewModel.cancelSleepTimer() }
            ShadowToast.getTextOfLatestToast() shouldBe string(R.string.sleep_timer_off)
        }
    }

    @Test
    fun `choosing end of chapter starts the timer and says so`() {
        val viewModel = mockedPlayerViewModelForPlayerFragment()
        launch(viewModel).use { scenario ->
            scenario.openSleepTimerDialog().choose(string(R.string.sleep_timer_end_of_chapter))

            verify { viewModel.startSleepTimerEndOfChapter() }
            ShadowToast.getTextOfLatestToast() shouldBe string(R.string.sleep_timer_set_end_of_chapter)
        }
    }

    @Test
    fun `the playback info overflow action toggles the playback info panel`() {
        launch(mockedPlayerViewModelForPlayerFragment()).use { scenario ->
            scenario.onFragment { fragment ->
                val playbackInfo = fragment.requireView().findViewById<View>(R.id.playback_info)
                val initiallyVisible = playbackInfo.isVisible

                fragment.overflowMenu().performIdentifierAction(R.id.action_playback_info, 0)

                playbackInfo.isVisible shouldBe !initiallyVisible
            }
        }
    }

    @Test
    fun `the speed overflow action opens without crashing`() {
        launch(mockedPlayerViewModelForPlayerFragment()).use { scenario ->
            scenario.onFragment { fragment ->
                fragment.overflowMenu().performIdentifierAction(R.id.action_speed, 0)
            }
        }
    }

    // --- Public callback-target methods (called by PlayerMenus/dialogs; already public, no UI
    // simulation needed) ------------------------------------------------------------------------
}
