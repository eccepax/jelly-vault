package org.jellyfin.mobile.music.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jellyfin.mobile.music.catalog.PlaylistStore
import org.jellyfin.mobile.music.domain.Playlist
import org.jellyfin.mobile.music.domain.PlaylistEntry
import org.jellyfin.mobile.music.domain.Track
import org.jellyfin.mobile.music.downloads.CollectionDownloads
import org.jellyfin.mobile.music.downloads.DownloadStatus
import org.jellyfin.mobile.music.playback.PlaybackController
import timber.log.Timber

sealed interface PlaylistDetailUiState {
    data object Loading : PlaylistDetailUiState
    data class Loaded(val playlist: Playlist, val entries: List<PlaylistEntry>) : PlaylistDetailUiState
    data object NotFound : PlaylistDetailUiState
    data object Error : PlaylistDetailUiState
}

class PlaylistDetailViewModel(
    private val playlistId: String,
    private val store: PlaylistStore,
    private val playback: PlaybackController,
    private val downloads: CollectionDownloads,
) : ViewModel() {

    private val _state = MutableStateFlow<PlaylistDetailUiState>(PlaylistDetailUiState.Loading)
    val state: StateFlow<PlaylistDetailUiState> = _state.asStateFlow()

    val downloadStatus: StateFlow<DownloadStatus> =
        downloads.status(playlistId).stateIn(viewModelScope, SharingStarted.Eagerly, DownloadStatus.NotDownloaded)

    init {
        refresh()
    }

    fun download() {
        val loaded = _state.value as? PlaylistDetailUiState.Loaded ?: return
        viewModelScope.launch { downloads.downloadPlaylist(loaded.playlist, loaded.entries.map { it.track }) }
    }

    fun removeDownload() {
        viewModelScope.launch { downloads.remove(playlistId) }
    }

    fun retryDownload() {
        viewModelScope.launch { downloads.retry(playlistId) }
    }

    /** A downloaded playlist follows the server's version of it; others are left alone. */
    private suspend fun syncDownload(loaded: PlaylistDetailUiState.Loaded) =
        downloads.updatePlaylist(loaded.playlist, loaded.entries.map { it.track })

    fun refresh() {
        if (_state.value !is PlaylistDetailUiState.Loaded) _state.value = PlaylistDetailUiState.Loading
        viewModelScope.launch {
            try {
                val loaded = coroutineScope {
                    val playlist = async { store.playlists().firstOrNull { it.id == playlistId } }
                    val entries = async { store.playlistEntries(playlistId) }
                    playlist.await()?.let { PlaylistDetailUiState.Loaded(it, entries.await()) }
                        ?: PlaylistDetailUiState.NotFound
                }
                _state.value = loaded
                if (loaded is PlaylistDetailUiState.Loaded) syncDownload(loaded)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.e(e, "Failed to load playlist $playlistId")
                if (_state.value !is PlaylistDetailUiState.Loaded) _state.value = PlaylistDetailUiState.Error
            }
        }
    }

    fun playAll() = withTracks { playback.play(it, startIndex = 0) }
    fun shuffle() = withTracks { playback.playShuffled(it) }
    fun playTrack(index: Int) = withTracks { playback.play(it, startIndex = index) }

    /** Takes the song out at once; if the server refuses, the playlist is reloaded as it really is. */
    fun remove(index: Int) {
        val loaded = _state.value as? PlaylistDetailUiState.Loaded ?: return
        val entry = loaded.entries.getOrNull(index) ?: return
        _state.value = loaded.copy(entries = loaded.entries - entry)
        viewModelScope.launch {
            try {
                store.removeFromPlaylist(playlistId, entry.entryId)
                (_state.value as? PlaylistDetailUiState.Loaded)?.let { syncDownload(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.e(e, "Couldn't remove from playlist $playlistId")
                _state.value = loaded
            }
        }
    }

    private fun withTracks(action: (List<Track>) -> Unit) {
        val tracks = (_state.value as? PlaylistDetailUiState.Loaded)?.entries?.map { it.track }.orEmpty()
        if (tracks.isNotEmpty()) action(tracks)
    }
}
