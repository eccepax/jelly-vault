package org.jellyfin.mobile.music.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
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
            AlbumsUiState.Loading -> Centered {
                CircularProgressIndicator(modifier = Modifier.testTag(AlbumsScreenTags.LOADING))
            }
            AlbumsUiState.Error -> Centered {
                Text(stringResource(R.string.albums_error))
                Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
                    Text(stringResource(R.string.retry))
                }
            }
            is AlbumsUiState.Loaded -> if (state.albums.isEmpty()) {
                Centered { Text(stringResource(R.string.albums_empty)) }
            } else {
                SortMenu(current = state.sort, onSortChange = onSortChange, modifier = Modifier.padding(start = 4.dp))
                AlbumGrid(albums = state.albums, onAlbumClick = onAlbumClick)
            }
        }
    }
}

/** [subtitle] is the line under each title: the artist by default. */
@Composable
internal fun AlbumGrid(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    subtitle: @Composable (Album) -> String = { artistOrUnknown(it.artist) },
) {
    LazyVerticalGrid(
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
    Column(modifier = modifier.clickable(onClick = onClick)) {
        AlbumCover(coverUrl = album.coverUrl, modifier = Modifier.fillMaxWidth())
        Text(
            text = album.title,
            style = MaterialTheme.typography.subtitle2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun AlbumCover(coverUrl: String?, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colors.surface),
    ) {
        Icon(Icons.Filled.Album, contentDescription = null, modifier = Modifier.size(48.dp))
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun Centered(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
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
