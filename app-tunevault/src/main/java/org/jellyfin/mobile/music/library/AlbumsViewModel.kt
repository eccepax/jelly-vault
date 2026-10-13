package org.jellyfin.mobile.music.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.mobile.music.app.LibraryPreferences
import org.jellyfin.mobile.music.catalog.MusicCatalog
import org.jellyfin.mobile.music.domain.Album
import org.jellyfin.mobile.music.domain.AlbumSort
import timber.log.Timber

sealed interface AlbumsUiState {
    data object Loading : AlbumsUiState
    data class Loaded(val albums: List<Album>, val sort: AlbumSort = AlbumSort.TITLE) : AlbumsUiState
    data object Error : AlbumsUiState
}

class AlbumsViewModel(
    private val catalog: MusicCatalog,
    private val preferences: LibraryPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow<AlbumsUiState>(AlbumsUiState.Loading)
    val state: StateFlow<AlbumsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    /** Re-sorts what's loaded (no reload) and remembers the choice. */
    fun setSort(sort: AlbumSort) {
        preferences.albumSort = sort
        _state.update { state -> if (state is AlbumsUiState.Loaded) loaded(state.albums, sort) else state }
    }

    private fun loaded(albums: List<Album>, sort: AlbumSort) = AlbumsUiState.Loaded(sort.sort(albums), sort)

    private fun load() {
        _state.value = AlbumsUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                loaded(catalog.albums(), preferences.albumSort)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.e(e, "Failed to load albums")
                AlbumsUiState.Error
            }
        }
    }
}
