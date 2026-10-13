package org.jellyfin.mobile.music.playlists

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jellyfin.mobile.music.catalog.FakePlaylistStore
import org.jellyfin.mobile.music.domain.track
import org.jellyfin.mobile.music.downloads.DownloadStatus
import org.jellyfin.mobile.music.downloads.FakeCollectionDownloads
import org.jellyfin.mobile.music.playback.FakePlaybackController
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDetailViewModelTest {

    private val store = FakePlaylistStore().apply { add("road", "Road trip", track("a"), track("b"), track("c")) }
    private val playback = FakePlaybackController()
    private val downloads = FakeCollectionDownloads()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun loaded(viewModel: PlaylistDetailViewModel) = viewModel.state.value as PlaylistDetailUiState.Loaded

    @Test
    fun `shows the playlist and its songs in order`() {
        val state = loaded(PlaylistDetailViewModel("road", store, playback, downloads))

        state.playlist.name shouldBe "Road trip"
        state.entries.map { it.track.id } shouldBe listOf("a", "b", "c")
    }

    @Test
    fun `an unknown playlist is not found`() {
        PlaylistDetailViewModel("nope", store, playback, downloads).state.value shouldBe PlaylistDetailUiState.NotFound
    }

    @Test
    fun `play, shuffle and tapping a song`() {
        val viewModel = PlaylistDetailViewModel("road", store, playback, downloads)

        viewModel.playAll()
        viewModel.shuffle()
        viewModel.playTrack(2)

        playback.commands shouldBe listOf("play a,b,c from 0", "playShuffled a,b,c", "play a,b,c from 2")
    }

    @Test
    fun `removing a song takes it out at once and on the server`() {
        val viewModel = PlaylistDetailViewModel("road", store, playback, downloads)

        viewModel.remove(1)

        loaded(viewModel).entries.map { it.track.id } shouldBe listOf("a", "c")
        store.writes shouldBe listOf("remove b from road")
    }

    @Test
    fun `a failed removal puts the song back`() {
        val viewModel = PlaylistDetailViewModel("road", store, playback, downloads)
        store.failure = IOException("offline")

        viewModel.remove(1)
        store.failure = null
        viewModel.refresh()

        loaded(viewModel).entries.map { it.track.id } shouldBe listOf("a", "b", "c")
    }

    @Test
    fun `a failed load shows an error`() {
        store.failure = IOException("offline")

        PlaylistDetailViewModel("road", store, playback, downloads).state.value shouldBe PlaylistDetailUiState.Error
    }

    @Test
    fun `downloading saves the playlist with its songs in order`() {
        val viewModel = PlaylistDetailViewModel("road", store, playback, downloads)

        viewModel.download()

        downloads.requests shouldBe listOf("update road: a,b,c", "playlist road: a,b,c")
    }

    @Test
    fun `opening a playlist brings its download up to date with the server`() {
        PlaylistDetailViewModel("road", store, playback, downloads)

        downloads.requests shouldBe listOf("update road: a,b,c")
    }

    @Test
    fun `removing a song updates the download too`() {
        val viewModel = PlaylistDetailViewModel("road", store, playback, downloads)

        viewModel.remove(1)

        downloads.requests.last() shouldBe "update road: a,c"
    }

    @Test
    fun `reports the playlist's download status, and removing acts on it`() {
        downloads.statuses.value = mapOf("road" to DownloadStatus.Downloaded)
        val viewModel = PlaylistDetailViewModel("road", store, playback, downloads)

        viewModel.downloadStatus.value shouldBe DownloadStatus.Downloaded
        viewModel.removeDownload()
        viewModel.retryDownload()

        downloads.requests.takeLast(2) shouldBe listOf("remove road", "retry road")
    }
}
