package org.jellyfin.mobile.music.nowplaying

import app.cash.turbine.test
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.mobile.music.domain.PlayQueue
import org.jellyfin.mobile.music.domain.tracks
import org.jellyfin.mobile.music.playback.FakePlaybackController
import org.jellyfin.mobile.music.playback.PlaybackState
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueueViewModelTest {

    private val playback = FakePlaybackController()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun playing(vararg ids: String, at: Int) {
        playback.state.value = PlaybackState(queue = PlayQueue.of(tracks(*ids), at), isPlaying = true)
    }

    @Test
    fun `shows the playing track and what's up next, hiding what's been played`() = runTest {
        playing("a", "b", "c", "d", at = 1)

        QueueViewModel(playback).state.test {
            val state = awaitItem()
            state.nowPlaying?.track?.id shouldBe "b"
            state.nowPlaying?.index shouldBe 1
            state.upNext.map { it.track.id } shouldBe listOf("c", "d")
            state.upNext.map { it.index } shouldBe listOf(2, 3)
        }
    }

    @Test
    fun `nothing playing is an empty queue`() = runTest {
        QueueViewModel(playback).state.test {
            awaitItem() shouldBe QueueUiState()
        }
    }

    @Test
    fun `follows the queue as it changes`() = runTest {
        playing("a", "b", at = 0)
        val viewModel = QueueViewModel(playback)

        viewModel.state.test {
            awaitItem().upNext.map { it.track.id } shouldBe listOf("b")
            playback.addToQueue(tracks("c"))
            awaitItem().upNext.map { it.track.id } shouldBe listOf("b", "c")
        }
    }

    @Test
    fun `queue edits go to the playback controller`() {
        playing("a", "b", "c", at = 0)
        val viewModel = QueueViewModel(playback)

        viewModel.remove(2)
        viewModel.move(from = 1, to = 2)
        viewModel.skipTo(1)

        playback.commands shouldBe listOf("remove 2", "move 1 to 2", "skipTo 1")
    }
}
