package org.jellyfin.mobile.music.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.mobile.music.catalog.PlaylistStore
import org.jellyfin.mobile.music.domain.Playlist
import org.jellyfin.mobile.music.domain.Track
import timber.log.Timber

sealed interface AddToPlaylistUiState {
    data object Closed : AddToPlaylistUiState
    data class Picking(val track: Track, val playlists: List<Playlist>) : AddToPlaylistUiState
}

/** What happened, for a brief confirmation. */
sealed interface AddToPlaylistResult {
    data class Added(val playlistName: String) : AddToPlaylistResult
    data class AlreadyThere(val playlistName: String) : AddToPlaylistResult
    data class Created(val playlistName: String) : AddToPlaylistResult
    data object Failed : AddToPlaylistResult
}

/**
 * The "Add to playlist" picker, shared by every song list (one per screen host). A song a playlist
 * already has is never added again: the server can't tell copies apart (see [PlaylistStore]).
 */
class AddToPlaylistViewModel(private val store: PlaylistStore) : ViewModel() {

    private val _state = MutableStateFlow<AddToPlaylistUiState>(AddToPlaylistUiState.Closed)
    val state: StateFlow<AddToPlaylistUiState> = _state.asStateFlow()

    private val _lastResult = MutableStateFlow<AddToPlaylistResult?>(null)
    val lastResult: StateFlow<AddToPlaylistResult?> = _lastResult.asStateFlow()

    fun request(track: Track) {
        _state.value = AddToPlaylistUiState.Picking(track, emptyList())
        launchReporting { _state.value = AddToPlaylistUiState.Picking(track, store.playlists()) }
    }

    fun addTo(playlistId: String) {
        val picking = _state.value as? AddToPlaylistUiState.Picking ?: return
        val name = picking.playlists.firstOrNull { it.id == playlistId }?.name.orEmpty()
        _state.value = AddToPlaylistUiState.Closed
        launchReporting {
            _lastResult.value = if (store.playlistEntries(playlistId).any { it.track.id == picking.track.id }) {
                AddToPlaylistResult.AlreadyThere(name)
            } else {
                store.addToPlaylist(playlistId, listOf(picking.track.id))
                AddToPlaylistResult.Added(name)
            }
        }
    }

    fun createWith(name: String) {
        val picking = _state.value as? AddToPlaylistUiState.Picking ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        _state.value = AddToPlaylistUiState.Closed
        launchReporting {
            store.createPlaylist(trimmed, listOf(picking.track.id))
            _lastResult.value = AddToPlaylistResult.Created(trimmed)
        }
    }

    fun dismiss() {
        _state.value = AddToPlaylistUiState.Closed
    }

    fun resultShown() {
        _lastResult.value = null
    }

    private fun launchReporting(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.e(e, "Add to playlist failed")
                _lastResult.value = AddToPlaylistResult.Failed
            }
        }
    }
}
