package org.jellyfin.mobile.music.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.mobile.music.catalog.MusicCatalog
import org.jellyfin.mobile.music.domain.Album
import org.jellyfin.mobile.music.domain.AlbumSort
import org.jellyfin.mobile.music.domain.Artist
import timber.log.Timber

sealed interface ArtistDetailUiState {
    data object Loading : ArtistDetailUiState
    data class Loaded(val artist: Artist, val albums: List<Album>) : ArtistDetailUiState
    data object NotFound : ArtistDetailUiState
    data object Error : ArtistDetailUiState
}

class ArtistDetailViewModel(
    private val artistId: String,
    private val catalog: MusicCatalog,
) : ViewModel() {

    private val _state = MutableStateFlow<ArtistDetailUiState>(ArtistDetailUiState.Loading)
    val state: StateFlow<ArtistDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        _state.value = ArtistDetailUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                coroutineScope {
                    val artist = async { catalog.artist(artistId) }
                    val albums = async { catalog.artistAlbums(artistId) }
                    artist.await()
                        ?.let { ArtistDetailUiState.Loaded(it, AlbumSort.YEAR.sort(albums.await())) }
                        ?: ArtistDetailUiState.NotFound
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.e(e, "Failed to load artist $artistId")
                ArtistDetailUiState.Error
            }
        }
    }
}
