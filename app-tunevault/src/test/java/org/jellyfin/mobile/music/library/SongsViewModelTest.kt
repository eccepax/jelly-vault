package org.jellyfin.mobile.music.library

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jellyfin.mobile.music.catalog.FakeMusicCatalog
import org.jellyfin.mobile.music.domain.track
import org.jellyfin.mobile.music.playback.FakePlaybackController
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SongsViewModelTest {

    private val catalog = FakeMusicCatalog(
        tracksByAlbum = mapOf(
            "x" to listOf(track("w").copy(title = "White & Nerdy"), track("t").copy(title = "The Saga Begins")),
            "y" to listOf(track("a").copy(title = "Around the World")),
        ),
    )
    private val playback = FakePlaybackController()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun loadedIds(viewModel: SongsViewModel) =
        (viewModel.state.value as SongsUiState.Loaded).tracks.map { it.id }

    @Test
    fun `lists every song sorted by title, ignoring articles`() {
        loadedIds(SongsViewModel(catalog, playback)) shouldBe listOf("a", "t", "w")
    }

    @Test
    fun `tapping a song plays the whole list from it`() {
        val viewModel = SongsViewModel(catalog, playback)

        viewModel.playFrom(1)

        playback.commands shouldBe listOf("play a,t,w from 1")
    }

    @Test
    fun `shuffle all shuffles every song`() {
        val viewModel = SongsViewModel(catalog, playback)

        viewModel.shuffleAll()

        playback.commands shouldBe listOf("playShuffled a,t,w")
    }

    @Test
    fun `play actions do nothing until loaded`() {
        catalog.gate = CompletableDeferred()
        val viewModel = SongsViewModel(catalog, playback)

        viewModel.playFrom(0)
        viewModel.shuffleAll()

        playback.commands shouldBe emptyList()
    }

    @Test
    fun `a failed load shows an error and retry recovers`() {
        catalog.failure = IOException("offline")
        val viewModel = SongsViewModel(catalog, playback)
        viewModel.state.value shouldBe SongsUiState.Error

        catalog.failure = null
        viewModel.retry()

        loadedIds(viewModel) shouldBe listOf("a", "t", "w")
    }
}
