package org.jellyfin.mobile.music.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jellyfin.mobile.music.catalog.FavoritesRepository
import org.jellyfin.mobile.music.catalog.MusicCatalog
import org.jellyfin.mobile.music.domain.Album
import org.jellyfin.mobile.music.domain.Track
import org.jellyfin.mobile.music.downloads.CollectionDownloads
import org.jellyfin.mobile.music.downloads.DownloadStatus
import org.jellyfin.mobile.music.playback.PlaybackController
import timber.log.Timber

sealed interface AlbumDetailUiState {
    data object Loading : AlbumDetailUiState
    data class Loaded(val album: Album, val tracks: List<Track>) : AlbumDetailUiState {
        val totalDurationMs: Long get() = tracks.sumOf { it.durationMs }
    }
    data object NotFound : AlbumDetailUiState
    data object Error : AlbumDetailUiState
}

class AlbumDetailViewModel(
    private val albumId: String,
    private val catalog: MusicCatalog,
    private val playback: PlaybackController,
    private val favorites: FavoritesRepository,
    private val downloads: CollectionDownloads,
) : ViewModel() {

    private val _state = MutableStateFlow<AlbumDetailUiState>(AlbumDetailUiState.Loading)
    val state: StateFlow<AlbumDetailUiState> = _state.asStateFlow()

    /** The album's heart: the server's flag, or a change made in this session. */
    val isFavorite: StateFlow<Boolean> = combine(_state, favorites.overrides) { state, _ ->
        (state as? AlbumDetailUiState.Loaded)?.album?.let { favorites.isFavorite(it.id, it.isFavorite) } ?: false
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val downloadStatus: StateFlow<DownloadStatus> =
        downloads.status(albumId).stateIn(viewModelScope, SharingStarted.Eagerly, DownloadStatus.NotDownloaded)

    init {
        load()
    }

    fun download() {
        val loaded = _state.value as? AlbumDetailUiState.Loaded ?: return
        viewModelScope.launch { downloads.downloadAlbum(loaded.album, loaded.tracks) }
    }

    fun removeDownload() {
        viewModelScope.launch { downloads.remove(albumId) }
    }

    fun retryDownload() {
        viewModelScope.launch { downloads.retry(albumId) }
    }

    fun retry() = load()

    fun playTrack(index: Int) = withTracks { playback.play(it, startIndex = index) }

    fun playAll() = withTracks { playback.play(it, startIndex = 0) }

    fun shuffle() = withTracks { playback.playShuffled(it) }

    fun toggleFavorite() {
        val album = (_state.value as? AlbumDetailUiState.Loaded)?.album ?: return
        favorites.setFavorite(album.id, !favorites.isFavorite(album.id, album.isFavorite))
    }

    private fun withTracks(action: (List<Track>) -> Unit) {
        (_state.value as? AlbumDetailUiState.Loaded)?.tracks?.takeIf { it.isNotEmpty() }?.let(action)
    }

    private fun load() {
        _state.value = AlbumDetailUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                coroutineScope {
                    val album = async { catalog.album(albumId) }
                    val tracks = async { catalog.albumTracks(albumId) }
                    album.await()?.let { AlbumDetailUiState.Loaded(it, tracks.await()) } ?: AlbumDetailUiState.NotFound
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.e(e, "Failed to load album $albumId")
                AlbumDetailUiState.Error
            }
        }
    }
}
