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
import timber.log.Timber

sealed interface PlaylistsUiState {
    data object Loading : PlaylistsUiState
    data class Loaded(val playlists: List<Playlist>) : PlaylistsUiState
    data object Error : PlaylistsUiState
}

class PlaylistsViewModel(private val store: PlaylistStore) : ViewModel() {

    private val _state = MutableStateFlow<PlaylistsUiState>(PlaylistsUiState.Loading)
    val state: StateFlow<PlaylistsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value !is PlaylistsUiState.Loaded) _state.value = PlaylistsUiState.Loading
        viewModelScope.launch {
            try {
                _state.value = PlaylistsUiState.Loaded(store.playlists())
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.e(e, "Failed to load playlists")
                if (_state.value !is PlaylistsUiState.Loaded) _state.value = PlaylistsUiState.Error
            }
        }
    }

    /** Creates an empty playlist; a blank name does nothing. */
    fun createPlaylist(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                store.createPlaylist(trimmed, emptyList())
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.e(e, "Failed to create playlist")
            }
        }
    }
}
