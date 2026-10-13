package org.jellyfin.mobile.music.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.music.R
import org.jellyfin.mobile.music.catalog.SearchResults
import org.jellyfin.mobile.music.domain.Album
import org.jellyfin.mobile.music.domain.Artist
import org.jellyfin.mobile.music.library.AlbumCover
import org.jellyfin.mobile.music.library.ArtistRow
import org.jellyfin.mobile.music.library.Centered
import org.jellyfin.mobile.music.library.SongRow
import org.jellyfin.mobile.music.library.TrackListActions
import org.jellyfin.mobile.music.library.trackListActions
import org.jellyfin.mobile.music.ui.artistOrUnknown
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SearchScreen(
    query: String,
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onArtistClick: (Artist) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onTrackClick: (index: Int) -> Unit,
    trackActions: TrackListActions = TrackListActions(),
) {
    Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.systemBarsPadding()) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.search_clear))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            when (state) {
                SearchUiState.Idle -> Centered { Text(stringResource(R.string.search_idle)) }
                SearchUiState.Searching -> Centered {
                    CircularProgressIndicator(modifier = Modifier.testTag(SearchScreenTags.SEARCHING))
                }
                is SearchUiState.NoResults -> Centered { Text(stringResource(R.string.search_no_results, state.query)) }
                SearchUiState.Error -> Centered {
                    Text(stringResource(R.string.search_error))
                    Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
                        Text(stringResource(R.string.retry))
                    }
                }
                is SearchUiState.Results -> Results(
                    results = state.results,
                    onArtistClick = onArtistClick,
                    onAlbumClick = onAlbumClick,
                    onTrackClick = onTrackClick,
                    trackActions = trackActions,
                )
            }
        }
    }
}

@Composable
private fun Results(
    results: SearchResults,
    onArtistClick: (Artist) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onTrackClick: (index: Int) -> Unit,
    trackActions: TrackListActions,
) {
    LazyColumn {
        if (results.artists.isNotEmpty()) {
            sectionHeader(R.string.artists_title)
            items(
                results.artists,
                key = { "artist:${it.id}" },
            ) { ArtistRow(artist = it, onClick = { onArtistClick(it) }) }
        }
        if (results.albums.isNotEmpty()) {
            sectionHeader(R.string.albums_title)
            items(results.albums, key = { "album:${it.id}" }) { AlbumRow(album = it, onClick = { onAlbumClick(it) }) }
        }
        if (results.tracks.isNotEmpty()) {
            sectionHeader(R.string.songs_title)
            itemsIndexed(results.tracks, key = { _, track -> "track:${track.id}" }) { index, track ->
                SongRow(
                    track = track,
                    onClick = { onTrackClick(index) },
                    onPlayNext = { trackActions.onPlayNext(track) },
                    onAddToQueue = { trackActions.onAddToQueue(track) },
                    isCurrent = track.id == trackActions.currentTrackId,
                    onAddToPlaylist = trackActions.onAddToPlaylist?.let { add -> { add(track) } },
                    isDownloaded = track.id in trackActions.downloadedTrackIds,
                )
            }
        }
    }
}

private fun LazyListScope.sectionHeader(title: Int) {
    item(key = "header:$title") {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.subtitle1,
            color = MaterialTheme.colors.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun AlbumRow(album: Album, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        AlbumCover(coverUrl = album.coverUrl, modifier = Modifier.size(44.dp))
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.body1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.album_result_subtitle, artistOrUnknown(album.artist)),
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun SearchRoute(
    onArtistClick: (Artist) -> Unit,
    onAlbumClick: (Album) -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val state by viewModel.state.collectAsState()
    SearchScreen(
        query = query,
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onRetry = viewModel::retry,
        onArtistClick = onArtistClick,
        onAlbumClick = onAlbumClick,
        onTrackClick = viewModel::playTrack,
        trackActions = trackListActions(),
    )
}
