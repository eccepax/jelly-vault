package org.jellyfin.mobile.music.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.common.ui.ScrollToTopOnChange
import org.jellyfin.mobile.common.ui.components.CoverArt
import org.jellyfin.mobile.common.ui.components.EmptyState
import org.jellyfin.mobile.common.ui.components.ErrorState
import org.jellyfin.mobile.common.ui.components.LoadingState
import org.jellyfin.mobile.common.ui.components.MediaGridCard
import org.jellyfin.mobile.music.R
import org.jellyfin.mobile.music.domain.Album
import org.jellyfin.mobile.music.domain.AlbumSort
import org.jellyfin.mobile.music.ui.artistOrUnknown
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AlbumsScreen(
    state: AlbumsUiState,
    onRetry: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onSortChange: (AlbumSort) -> Unit = {},
) {
    // Tab content: LibraryScreen provides the surface, header and system-bar padding.
    Column {
        when (state) {
            AlbumsUiState.Loading -> LoadingState(Modifier.testTag(AlbumsScreenTags.LOADING))
            AlbumsUiState.Error -> ErrorState(stringResource(R.string.albums_error), onRetry = onRetry)
            is AlbumsUiState.Loaded -> if (state.albums.isEmpty()) {
                EmptyState(stringResource(R.string.albums_empty))
            } else {
                SortMenu(current = state.sort, onSortChange = onSortChange, modifier = Modifier.padding(start = 4.dp))
                AlbumGrid(albums = state.albums, onAlbumClick = onAlbumClick, scrollKey = state.sort.name)
            }
        }
    }
}

/**
 * [subtitle] is the line under each title: the artist by default. A new [scrollKey] (a new sort)
 * takes the grid back to its top.
 */
@Composable
internal fun AlbumGrid(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    subtitle: @Composable (Album) -> String = { artistOrUnknown(it.artist) },
    scrollKey: String = "",
) {
    val grid = rememberLazyGridState()
    ScrollToTopOnChange(scrollKey) { grid.scrollToItem(0) }
    LazyVerticalGrid(
        state = grid,
        modifier = Modifier.testTag(AlbumsScreenTags.GRID),
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(albums, key = { it.id }) { album ->
            AlbumCard(album = album, subtitle = subtitle(album), onClick = { onAlbumClick(album) })
        }
    }
}

@Composable
internal fun AlbumCard(album: Album, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    MediaGridCard(
        title = album.title,
        subtitle = subtitle,
        cover = { AlbumCover(coverUrl = album.coverUrl, modifier = Modifier.fillMaxWidth()) },
        onClick = onClick,
        modifier = modifier,
    )
}

/** An album's cover, or a record while there's none. */
@Composable
fun AlbumCover(coverUrl: String?, modifier: Modifier = Modifier) {
    CoverArt(url = coverUrl, placeholder = Icons.Filled.Album, modifier = modifier)
}

@Composable
fun AlbumsRoute(onAlbumClick: (Album) -> Unit, viewModel: AlbumsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    AlbumsScreen(
        state = state,
        onRetry = viewModel::retry,
        onAlbumClick = onAlbumClick,
        onSortChange = viewModel::setSort,
    )
}
