package org.jellyfin.mobile.music.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.cast.CastButton
import org.jellyfin.mobile.cast.Casting
import org.jellyfin.mobile.common.ui.components.ErrorState
import org.jellyfin.mobile.common.ui.components.LoadingState
import org.jellyfin.mobile.common.ui.components.SectionTitle
import org.jellyfin.mobile.common.ui.components.VaultTopBar
import org.jellyfin.mobile.common.ui.theme.VaultAlpha
import org.jellyfin.mobile.music.R
import org.jellyfin.mobile.music.domain.Album
import org.jellyfin.mobile.music.domain.Track
import org.jellyfin.mobile.music.library.AlbumCard
import org.jellyfin.mobile.music.offline.ServerConnectivity
import org.jellyfin.mobile.music.ui.artistOrUnknown
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    state: HomeUiState,
    onRetry: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onShuffleAll: () -> Unit,
    onFavoriteSongClick: (index: Int) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    isOffline: Boolean = false,
    castButton: @Composable () -> Unit = {},
) {
    Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.systemBarsPadding()) {
            VaultTopBar(
                title = stringResource(R.string.home_title),
                actions = {
                    castButton()
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
            when (state) {
                HomeUiState.Loading -> LoadingState(Modifier.testTag(HomeScreenTags.LOADING))
                HomeUiState.Error -> ErrorState(stringResource(R.string.home_error), onRetry = onRetry)
                is HomeUiState.Loaded -> if (isOffline) {
                    OfflineShelves(state, onAlbumClick, onShuffleAll)
                } else {
                    Shelves(state, onAlbumClick, onShuffleAll, onFavoriteSongClick)
                }
            }
        }
    }
}

/** Offline, Home's newest shelf holds the downloads (see DownloadedLibrary); the rest need the server. */
@Composable
private fun OfflineShelves(state: HomeUiState.Loaded, onAlbumClick: (Album) -> Unit, onShuffleAll: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        if (state.recentlyAdded.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.offline_nothing_downloaded),
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.padding(16.dp),
                )
            }
            return@LazyColumn
        }
        item { ShuffleAllButton(onShuffleAll) }
        item { ShelfTitle(R.string.your_downloads) }
        item { Shelf(state.recentlyAdded, onAlbumClick) }
    }
}

@Composable
private fun ShuffleAllButton(onShuffleAll: () -> Unit) {
    Button(onClick = onShuffleAll, modifier = Modifier.padding(16.dp)) {
        Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.shuffle_all))
    }
}

@Composable
private fun Shelves(
    state: HomeUiState.Loaded,
    onAlbumClick: (Album) -> Unit,
    onShuffleAll: () -> Unit,
    onFavoriteSongClick: (index: Int) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item { ShuffleAllButton(onShuffleAll) }
        item { ShelfTitle(R.string.recently_played) }
        item {
            if (state.recentlyPlayed.isEmpty()) {
                Text(
                    text = stringResource(R.string.recently_played_empty),
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onBackground.copy(alpha = VaultAlpha.SECONDARY),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                Shelf(state.recentlyPlayed, onAlbumClick, Modifier.testTag(HomeScreenTags.RECENTLY_PLAYED))
            }
        }
        if (state.favoriteAlbums.isNotEmpty()) {
            item { ShelfTitle(R.string.favorite_albums) }
            item { Shelf(state.favoriteAlbums, onAlbumClick) }
        }
        if (state.favoriteTracks.isNotEmpty()) {
            item { ShelfTitle(R.string.favorite_songs) }
            item { SongShelf(state.favoriteTracks, onFavoriteSongClick) }
        }
        if (state.recentlyAdded.isNotEmpty()) {
            item { ShelfTitle(R.string.recently_added) }
            item { Shelf(state.recentlyAdded, onAlbumClick) }
        }
    }
}

@Composable
private fun ShelfTitle(title: Int) {
    SectionTitle(stringResource(title))
}

@Composable
private fun Shelf(albums: List<Album>, onAlbumClick: (Album) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        items(albums, key = { it.id }) { album ->
            AlbumCard(
                album = album,
                subtitle = artistOrUnknown(album.artist),
                onClick = { onAlbumClick(album) },
                modifier = Modifier.width(SHELF_CARD_WIDTH.dp),
            )
        }
    }
}

/** Songs as cover cards: the card shows the song title and its artist. */
@Composable
private fun SongShelf(tracks: List<Track>, onSongClick: (index: Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            AlbumCard(
                album = Album(
                    id = track.id,
                    title = track.title,
                    artist = track.artist,
                    year = null,
                    coverUrl = track.coverUrl,
                ),
                subtitle = artistOrUnknown(track.artist),
                onClick = { onSongClick(index) },
                modifier = Modifier.width(SHELF_CARD_WIDTH.dp),
            )
        }
    }
}

private const val SHELF_CARD_WIDTH = 140

/** Refreshes each time Home is shown, so it reflects what was just played. */
@Composable
fun HomeRoute(onAlbumClick: (Album) -> Unit, onSettingsClick: () -> Unit, viewModel: HomeViewModel = koinViewModel()) {
    val isOffline by koinInject<ServerConnectivity>().isOffline.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    val state by viewModel.state.collectAsState()
    val casting: Casting = koinInject()
    val castButton: @Composable () -> Unit = remember(casting) { { CastButton(casting) } }
    HomeScreen(
        state = state,
        onRetry = viewModel::refresh,
        onAlbumClick = onAlbumClick,
        onShuffleAll = viewModel::shuffleAll,
        onFavoriteSongClick = viewModel::playFavorite,
        onSettingsClick = onSettingsClick,
        isOffline = isOffline,
        castButton = castButton,
    )
}
