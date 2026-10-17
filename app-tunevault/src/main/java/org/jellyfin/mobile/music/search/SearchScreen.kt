package org.jellyfin.mobile.music.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.common.ui.components.EmptyState
import org.jellyfin.mobile.common.ui.components.ErrorState
import org.jellyfin.mobile.common.ui.components.LoadingState
import org.jellyfin.mobile.common.ui.components.MediaListRow
import org.jellyfin.mobile.common.ui.components.SearchField
import org.jellyfin.mobile.common.ui.theme.VaultSpacing
import org.jellyfin.mobile.music.R
import org.jellyfin.mobile.music.catalog.SearchResults
import org.jellyfin.mobile.music.domain.Album
import org.jellyfin.mobile.music.domain.Artist
import org.jellyfin.mobile.music.library.AlbumCover
import org.jellyfin.mobile.music.library.ArtistRow
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
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = stringResource(R.string.search_hint),
                modifier = Modifier.padding(top = VaultSpacing.Small),
            )
            when (state) {
                SearchUiState.Idle -> EmptyState(stringResource(R.string.search_idle))
                SearchUiState.Searching -> LoadingState(Modifier.testTag(SearchScreenTags.SEARCHING))
                is SearchUiState.NoResults -> EmptyState(stringResource(R.string.search_no_results, state.query))
                SearchUiState.Error -> ErrorState(stringResource(R.string.search_error), onRetry = onRetry)
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
    MediaListRow(
        title = album.title,
        subtitle = stringResource(R.string.album_result_subtitle, artistOrUnknown(album.artist)),
        cover = { AlbumCover(coverUrl = album.coverUrl) },
        onClick = onClick,
    )
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
