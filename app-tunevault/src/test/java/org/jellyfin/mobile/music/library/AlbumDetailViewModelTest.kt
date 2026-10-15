package org.jellyfin.mobile.music.library

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jellyfin.mobile.common.downloads.GroupStatus
import org.jellyfin.mobile.music.catalog.FakeFavoritesStore
import org.jellyfin.mobile.music.catalog.FakeMusicCatalog
import org.jellyfin.mobile.music.catalog.FavoritesRepository
import org.jellyfin.mobile.music.catalog.album
import org.jellyfin.mobile.music.domain.track
import org.jellyfin.mobile.music.downloads.FakeCollectionDownloads
import org.jellyfin.mobile.music.playback.FakePlaybackController
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumDetailViewModelTest {

    private val purpleBird = album("pb", "The Purple Bird")
    private val tracks = listOf(track("1"), track("2"))
    private val catalog = FakeMusicCatalog(albums = listOf(purpleBird), tracksByAlbum = mapOf("pb" to tracks))
    private val playback = FakePlaybackController()
    private val favoritesStore = FakeFavoritesStore()
    private val favorites = FavoritesRepository(favoritesStore, TestScope(UnconfinedTestDispatcher()))
    private val downloads = FakeCollectionDownloads()

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

        val viewModel = AlbumDetailViewModel("pb", catalog, playback, favorites, downloads)
        viewModel.state.value shouldBe AlbumDetailUiState.Loading

        gate.complete(Unit)
        viewModel.state.value shouldBe AlbumDetailUiState.Loaded(purpleBird, tracks)
    }

    @Test
    fun `shows the album and its tracks`() {
        AlbumDetailViewModel("pb", catalog, playback, favorites, downloads).state.value shouldBe AlbumDetailUiState.Loaded(purpleBird, tracks)
    }

    @Test
    fun `an unknown album is not found, not an error`() {
        AlbumDetailViewModel("nope", catalog, playback, favorites, downloads).state.value shouldBe AlbumDetailUiState.NotFound
    }

    @Test
    fun `a failed load shows an error and retry recovers`() {
        catalog.failure = IOException("offline")
        val viewModel = AlbumDetailViewModel("pb", catalog, playback, favorites, downloads)
        viewModel.state.value shouldBe AlbumDetailUiState.Error

        catalog.failure = null
        viewModel.retry()

        viewModel.state.value shouldBe AlbumDetailUiState.Loaded(purpleBird, tracks)
    }

    @Test
    fun `tapping a track plays the album from that track`() {
        AlbumDetailViewModel("pb", catalog, playback, favorites, downloads).playTrack(1)

        playback.commands shouldBe listOf("play 1,2 from 1")
    }

    @Test
    fun `play plays the album from the start`() {
        AlbumDetailViewModel("pb", catalog, playback, favorites, downloads).playAll()

        playback.commands shouldBe listOf("play 1,2 from 0")
    }

    @Test
    fun `shuffle plays the album shuffled`() {
        AlbumDetailViewModel("pb", catalog, playback, favorites, downloads).shuffle()

        playback.commands shouldBe listOf("playShuffled 1,2")
    }

    @Test
    fun `play actions do nothing until the album has loaded`() {
        catalog.gate = CompletableDeferred()
        val viewModel = AlbumDetailViewModel("pb", catalog, playback, favorites, downloads)

        viewModel.playAll()
        viewModel.playTrack(0)
        viewModel.shuffle()

        playback.commands shouldBe emptyList()
    }

    @Test
    fun `reports whether the album is a favorite`() {
        catalog.albums = listOf(purpleBird.copy(isFavorite = true))

        AlbumDetailViewModel("pb", catalog, playback, favorites, downloads).isFavorite.value shouldBe true
    }

    @Test
    fun `toggling the album's heart shows at once and saves it`() {
        val viewModel = AlbumDetailViewModel("pb", catalog, playback, favorites, downloads)
        viewModel.isFavorite.value shouldBe false

        viewModel.toggleFavorite()

        viewModel.isFavorite.value shouldBe true
        favoritesStore.writes shouldBe listOf("pb=true")
    }

    @Test
    fun `loaded state totals the track durations`() {
        val loaded = AlbumDetailUiState.Loaded(purpleBird, listOf(track("1"), track("2"), track("3")))

        loaded.totalDurationMs shouldBe 3 * track("1").durationMs
    }

    @Test
    fun `downloading saves the album with its tracks`() {
        val viewModel = AlbumDetailViewModel("pb", catalog, playback, favorites, downloads)

        viewModel.download()

        downloads.requests shouldBe listOf("album pb: 1,2")
    }

    @Test
    fun `reports the album's download status`() {
        downloads.statuses.value = mapOf("pb" to GroupStatus.InProgress(done = 1, total = 2))

        AlbumDetailViewModel("pb", catalog, playback, favorites, downloads).downloadStatus.value shouldBe
            GroupStatus.InProgress(done = 1, total = 2)
    }

    @Test
    fun `removing and retrying act on this album`() {
        val viewModel = AlbumDetailViewModel("pb", catalog, playback, favorites, downloads)

        viewModel.removeDownload()
        viewModel.retryDownload()

        downloads.requests shouldBe listOf("remove pb", "retry pb")
    }

    @Test
    fun `download does nothing until the album has loaded`() {
        catalog.gate = CompletableDeferred()
        val viewModel = AlbumDetailViewModel("pb", catalog, playback, favorites, downloads)

        viewModel.download()

        downloads.requests shouldBe emptyList()
    }
}
