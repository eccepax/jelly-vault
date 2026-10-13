package org.jellyfin.mobile.music.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.mobile.music.catalog.MusicCatalog
import org.jellyfin.mobile.music.domain.Artist
import org.jellyfin.mobile.music.domain.sortKey
import timber.log.Timber

sealed interface ArtistsUiState {
    data object Loading : ArtistsUiState
    data class Loaded(val artists: List<Artist>) : ArtistsUiState
    data object Error : ArtistsUiState
}

class ArtistsViewModel(private val catalog: MusicCatalog) : ViewModel() {

    private val _state = MutableStateFlow<ArtistsUiState>(ArtistsUiState.Loading)
    val state: StateFlow<ArtistsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        _state.value = ArtistsUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                ArtistsUiState.Loaded(catalog.albumArtists().sortedBy { sortKey(it.name) })
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.e(e, "Failed to load artists")
                ArtistsUiState.Error
            }
        }
    }
}
