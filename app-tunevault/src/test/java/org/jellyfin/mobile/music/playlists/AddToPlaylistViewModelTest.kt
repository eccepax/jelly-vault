package org.jellyfin.mobile.music.playlists

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jellyfin.mobile.music.catalog.FakePlaylistStore
import org.jellyfin.mobile.music.domain.track
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddToPlaylistViewModelTest {

    private val song = track("s").copy(title = "Da Funk")
    private val store = FakePlaylistStore(library = mapOf("s" to song)).apply {
        add("road", "Road trip", track("a"))
        add("has", "Already has it", song)
    }
    private val viewModel = AddToPlaylistViewModel(store)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `nothing is being added until a song is chosen`() {
        viewModel.state.value shouldBe AddToPlaylistUiState.Closed
    }

    @Test
    fun `choosing a song opens the picker with the playlists`() {
        viewModel.request(song)

        val picking = viewModel.state.value as AddToPlaylistUiState.Picking
        picking.track shouldBe song
        picking.playlists.map { it.name } shouldBe listOf("Already has it", "Road trip")
    }

    @Test
    fun `picking a playlist adds the song and closes`() {
        viewModel.request(song)

        viewModel.addTo("road")

        store.writes shouldBe listOf("add s to road")
        viewModel.state.value shouldBe AddToPlaylistUiState.Closed
        viewModel.lastResult.value shouldBe AddToPlaylistResult.Added("Road trip")
    }

    @Test
    fun `a playlist that already has the song is left alone`() {
        viewModel.request(song)

        viewModel.addTo("has")

        store.writes shouldBe emptyList()
        viewModel.lastResult.value shouldBe AddToPlaylistResult.AlreadyThere("Already has it")
    }

    @Test
    fun `a new playlist can be made with the song in it`() {
        viewModel.request(song)

        viewModel.createWith("  Gym  ")

        store.writes shouldBe listOf("create 'Gym' with s")
        viewModel.state.value shouldBe AddToPlaylistUiState.Closed
        viewModel.lastResult.value shouldBe AddToPlaylistResult.Created("Gym")
    }

    @Test
    fun `dismissing closes without changes`() {
        viewModel.request(song)

        viewModel.dismiss()

        viewModel.state.value shouldBe AddToPlaylistUiState.Closed
        store.writes shouldBe emptyList()
    }

    @Test
    fun `a shown result can be cleared`() {
        viewModel.request(song)
        viewModel.addTo("road")

        viewModel.resultShown()

        viewModel.lastResult.value shouldBe null
    }
}
