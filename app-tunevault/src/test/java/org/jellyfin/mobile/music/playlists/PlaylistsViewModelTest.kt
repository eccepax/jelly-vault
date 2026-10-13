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
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistsViewModelTest {

    private val store = FakePlaylistStore().apply {
        add("road", "Road trip", track("a"), track("b"))
        add("chill", "Chill", track("c"))
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun names(viewModel: PlaylistsViewModel) =
        (viewModel.state.value as PlaylistsUiState.Loaded).playlists.map { it.name }

    @Test
    fun `lists the playlists by name`() {
        names(PlaylistsViewModel(store)) shouldBe listOf("Chill", "Road trip")
    }

    @Test
    fun `creating a playlist adds it to the list`() {
        val viewModel = PlaylistsViewModel(store)

        viewModel.createPlaylist("  Gym  ")

        store.writes shouldBe listOf("create 'Gym' with ")
        names(viewModel) shouldBe listOf("Chill", "Gym", "Road trip")
    }

    @Test
    fun `a blank name creates nothing`() {
        val viewModel = PlaylistsViewModel(store)

        viewModel.createPlaylist("   ")

        store.writes shouldBe emptyList()
    }

    @Test
    fun `a failed load shows an error and retry recovers`() {
        store.failure = IOException("offline")
        val viewModel = PlaylistsViewModel(store)
        viewModel.state.value shouldBe PlaylistsUiState.Error

        store.failure = null
        viewModel.refresh()

        names(viewModel) shouldBe listOf("Chill", "Road trip")
    }
}
