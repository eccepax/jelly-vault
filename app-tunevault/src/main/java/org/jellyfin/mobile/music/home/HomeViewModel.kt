package org.jellyfin.mobile.music.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.mobile.music.catalog.FavoritesStore
import org.jellyfin.mobile.music.catalog.HomeFeed
import org.jellyfin.mobile.music.catalog.MusicCatalog
import org.jellyfin.mobile.music.domain.Album
import org.jellyfin.mobile.music.domain.Track
import org.jellyfin.mobile.music.playback.PlaybackController
import timber.log.Timber

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Loaded(
        val recentlyPlayed: List<Album>,
        val recentlyAdded: List<Album>,
        val favoriteAlbums: List<Album> = emptyList(),
        val favoriteTracks: List<Track> = emptyList(),
    ) : HomeUiState
    data object Error : HomeUiState
}

class HomeViewModel(
    private val feed: HomeFeed,
    private val catalog: MusicCatalog,
    private val playback: PlaybackController,
    private val favorites: FavoritesStore,
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Reloads the shelves. Already-shown shelves stay up meanwhile, so returning to Home doesn't flash. */
    fun refresh() {
        if (_state.value !is HomeUiState.Loaded) _state.value = HomeUiState.Loading
        viewModelScope.launch {
            try {
                _state.value = coroutineScope {
                    val played = async { feed.recentlyPlayed(PLAYED_TRACKS) }
                    val added = async { feed.recentlyAdded(SHELF_SIZE) }
                    val favoriteAlbums = async { favorites.favoriteAlbums() }
                    val favoriteTracks = async { favorites.favoriteTracks() }
                    HomeUiState.Loaded(
                        recentlyPlayed = albumsOf(played.await()),
                        recentlyAdded = added.await(),
                        favoriteAlbums = favoriteAlbums.await(),
                        favoriteTracks = favoriteTracks.await(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.e(e, "Failed to load home")
                if (_state.value !is HomeUiState.Loaded) _state.value = HomeUiState.Error
            }
        }
    }

    /** Plays the favorite songs, starting from the one tapped. */
    fun playFavorite(index: Int) {
        val tracks = (_state.value as? HomeUiState.Loaded)?.favoriteTracks.orEmpty()
        if (index in tracks.indices) playback.play(tracks, startIndex = index)
    }

    fun shuffleAll() {
        viewModelScope.launch {
            try {
                catalog.tracks().takeIf { it.isNotEmpty() }?.let(playback::playShuffled)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.e(e, "Couldn't load the library to shuffle")
            }
        }
    }

    /** Each album once, in order of its most recent play; tracks with no album are skipped. */
    private fun albumsOf(tracks: List<Track>): List<Album> = tracks
        .filter { it.albumId.isNotBlank() }
        .distinctBy { it.albumId }
        .take(SHELF_SIZE)
        .map { Album(id = it.albumId, title = it.albumTitle, artist = it.artist, year = null, coverUrl = it.coverUrl) }

    private companion object {
        const val SHELF_SIZE = 12

        /** Enough recent plays to fill the shelf even when several come from one album. */
        const val PLAYED_TRACKS = 100
    }
}
