package org.jellyfin.mobile.music.library

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jellyfin.mobile.music.app.FakeLibraryPreferences
import org.jellyfin.mobile.music.catalog.FakeMusicCatalog
import org.jellyfin.mobile.music.catalog.album
import org.jellyfin.mobile.music.domain.AlbumSort
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumsViewModelTest {

    private val catalog = FakeMusicCatalog()
    private val preferences = FakeLibraryPreferences()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `shows loading until the catalog answers`() {
        val gate = CompletableDeferred<Unit>()
        catalog.gate = gate
        catalog.albums = listOf(album("1"))

        val viewModel = AlbumsViewModel(catalog, preferences)
        viewModel.state.value shouldBe AlbumsUiState.Loading

        gate.complete(Unit)
        viewModel.state.value shouldBe AlbumsUiState.Loaded(listOf(album("1")))
    }

    @Test
    fun `shows the catalog's albums`() {
        catalog.albums = listOf(album("1"), album("2"))

        AlbumsViewModel(catalog, preferences).state.value shouldBe AlbumsUiState.Loaded(listOf(album("1"), album("2")))
    }

    @Test
    fun `an empty library is loaded, not an error`() {
        AlbumsViewModel(catalog, preferences).state.value shouldBe AlbumsUiState.Loaded(emptyList())
    }

    @Test
    fun `a failed load shows an error`() {
        catalog.failure = IOException("offline")

        AlbumsViewModel(catalog, preferences).state.value shouldBe AlbumsUiState.Error
    }

    @Test
    fun `retry after a failure loads the albums`() {
        catalog.failure = IOException("offline")
        val viewModel = AlbumsViewModel(catalog, preferences)

        catalog.failure = null
        catalog.albums = listOf(album("1"))
        viewModel.retry()

        viewModel.state.value shouldBe AlbumsUiState.Loaded(listOf(album("1")))
    }

    @Test
    fun `albums are sorted by the saved sort`() {
        preferences.albumSort = AlbumSort.YEAR
        catalog.albums = listOf(album("old", "Old", year = 1990), album("new", "New", year = 2020))

        val state = AlbumsViewModel(catalog, preferences).state.value as AlbumsUiState.Loaded

        state.albums.map { it.id } shouldBe listOf("new", "old")
        state.sort shouldBe AlbumSort.YEAR
    }

    @Test
    fun `changing the sort re-sorts without reloading and saves the choice`() {
        catalog.albums = listOf(album("b", "Bravo", year = 1990), album("a", "Alpha", year = 2020))
        val viewModel = AlbumsViewModel(catalog, preferences)

        viewModel.setSort(AlbumSort.YEAR)

        (viewModel.state.value as AlbumsUiState.Loaded).albums.map { it.id } shouldBe listOf("a", "b")
        preferences.albumSort shouldBe AlbumSort.YEAR
        catalog.albumsCalls shouldBe 1
    }

    @Test
    fun `loads once on creation, not again on each state read`() {
        val viewModel = AlbumsViewModel(catalog, preferences)
        viewModel.state.value
        viewModel.state.value

        catalog.albumsCalls shouldBe 1
    }
}
