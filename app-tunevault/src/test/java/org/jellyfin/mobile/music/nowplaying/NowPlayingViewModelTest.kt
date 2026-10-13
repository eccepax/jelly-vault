package org.jellyfin.mobile.music.nowplaying

import app.cash.turbine.test
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.mobile.music.catalog.FakeFavoritesStore
import org.jellyfin.mobile.music.catalog.FavoritesRepository
import org.jellyfin.mobile.music.domain.PlayQueue
import org.jellyfin.mobile.music.domain.RepeatMode
import org.jellyfin.mobile.music.domain.track
import org.jellyfin.mobile.music.domain.tracks
import org.jellyfin.mobile.music.playback.FakePlaybackController
import org.jellyfin.mobile.music.playback.PlaybackState
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingViewModelTest {

    private val playback = FakePlaybackController()
    private val favoritesStore = FakeFavoritesStore()
    private val favorites = FavoritesRepository(favoritesStore, TestScope(UnconfinedTestDispatcher()))

    @BeforeEach
    fun setUp() {
        // runTest picks up Main's scheduler, so the ticker's delay() runs on virtual time.
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun playing(vararg ids: String, isPlaying: Boolean = true) {
        playback.state.value = PlaybackState(queue = PlayQueue.of(tracks(*ids)), isPlaying = isPlaying)
    }

    @Test
    fun `nothing playing has no track`() = runTest {
        NowPlayingViewModel(playback, favorites).state.test {
            awaitItem().track shouldBe null
        }
    }

    @Test
    fun `reflects the current track and controls`() = runTest {
        playback.state.value = PlaybackState(
            queue = PlayQueue.of(tracks("a", "b")).withRepeatMode(RepeatMode.ALL),
            isPlaying = true,
        )

        NowPlayingViewModel(playback, favorites).state.test {
            val state = expectMostRecentItem()
            state.track?.id shouldBe "a"
            state.isPlaying shouldBe true
            state.durationMs shouldBe 180_000L
            state.repeatMode shouldBe RepeatMode.ALL
            state.isShuffled shouldBe false
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `falls back to the player's duration when the server doesn't know it`() = runTest {
        playback.state.value = PlaybackState(
            queue = PlayQueue.of(tracks("a").map { it.copy(durationMs = 0L) }),
            isPlaying = true,
        )
        playback.durationMs = 200_000L

        NowPlayingViewModel(playback, favorites).state.test {
            runCurrent()
            expectMostRecentItem().durationMs shouldBe 200_000L
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `position ticks while playing`() = runTest {
        playing("a")
        playback.positionMs = 1_000L

        NowPlayingViewModel(playback, favorites).state.test {
            expectMostRecentItem().positionMs shouldBe 1_000L

            playback.positionMs = 1_500L
            advanceTimeBy(NowPlayingViewModel.TICK_MS + 1)

            expectMostRecentItem().positionMs shouldBe 1_500L
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `position stops ticking while paused`() = runTest {
        playing("a", isPlaying = false)
        playback.positionMs = 1_000L

        NowPlayingViewModel(playback, favorites).state.test {
            runCurrent() // let stateIn start its upstream, which reads the position once
            expectMostRecentItem().positionMs shouldBe 1_000L

            playback.positionMs = 9_000L
            advanceTimeBy(NowPlayingViewModel.TICK_MS * 4)

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `seeking shows the new position immediately`() = runTest {
        playing("a", isPlaying = false)

        val viewModel = NowPlayingViewModel(playback, favorites)
        viewModel.state.test {
            runCurrent() // subscribed, as the screen is long before anyone seeks
            expectMostRecentItem()

            viewModel.seekTo(42_000L)
            runCurrent()

            expectMostRecentItem().positionMs shouldBe 42_000L
            playback.commands shouldBe listOf("seekTo 42000")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shows whether the playing song is a favorite`() = runTest {
        val favoriteSong = track("a").copy(isFavorite = true)
        playback.state.value = PlaybackState(queue = PlayQueue.of(listOf(favoriteSong)), isPlaying = true)

        NowPlayingViewModel(playback, favorites).state.test {
            runCurrent()
            expectMostRecentItem().isFavorite shouldBe true
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggling the heart shows at once and saves it`() = runTest {
        playing("a")
        val viewModel = NowPlayingViewModel(playback, favorites)

        viewModel.state.test {
            runCurrent()
            expectMostRecentItem().isFavorite shouldBe false

            viewModel.toggleFavorite()
            runCurrent()

            expectMostRecentItem().isFavorite shouldBe true
            favoritesStore.writes shouldBe listOf("a=true")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `transport commands go to the playback controller`() = runTest {
        val viewModel = NowPlayingViewModel(playback, favorites)

        viewModel.togglePlayPause()
        viewModel.skipToNext()
        viewModel.skipToPrevious()
        viewModel.cycleRepeatMode()
        viewModel.toggleShuffle()

        playback.commands shouldBe listOf(
            "togglePlayPause",
            "skipToNext",
            "skipToPrevious",
            "cycleRepeatMode",
            "toggleShuffle",
        )
    }
}
