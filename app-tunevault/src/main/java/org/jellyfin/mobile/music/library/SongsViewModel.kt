package org.jellyfin.mobile.music.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.mobile.music.catalog.MusicCatalog
import org.jellyfin.mobile.music.domain.Track
import org.jellyfin.mobile.music.domain.sortKey
import org.jellyfin.mobile.music.playback.PlaybackController
import timber.log.Timber

sealed interface SongsUiState {
    data object Loading : SongsUiState
    data class Loaded(val tracks: List<Track>) : SongsUiState
    data object Error : SongsUiState
}

class SongsViewModel(
    private val catalog: MusicCatalog,
    private val playback: PlaybackController,
) : ViewModel() {

    private val _state = MutableStateFlow<SongsUiState>(SongsUiState.Loading)
    val state: StateFlow<SongsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    /** Plays the whole list starting at [index], so playback carries on through the library. */
    fun playFrom(index: Int) = withTracks { playback.play(it, startIndex = index) }

    fun shuffleAll() = withTracks { playback.playShuffled(it) }

    private fun withTracks(action: (List<Track>) -> Unit) {
        (_state.value as? SongsUiState.Loaded)?.tracks?.takeIf { it.isNotEmpty() }?.let(action)
    }

    private fun load() {
        _state.value = SongsUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                SongsUiState.Loaded(catalog.tracks().sortedBy { sortKey(it.title) })
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.e(e, "Failed to load songs")
                SongsUiState.Error
            }
        }
    }
}
