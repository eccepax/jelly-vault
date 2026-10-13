package org.jellyfin.mobile.music.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.music.R
import org.jellyfin.mobile.music.domain.Track
import org.jellyfin.mobile.music.downloads.DownloadedMark
import org.jellyfin.mobile.music.ui.artistOrUnknown
import org.jellyfin.mobile.music.ui.formatDuration
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SongsScreen(
    state: SongsUiState,
    onRetry: () -> Unit,
    onSongClick: (index: Int) -> Unit,
    onShuffleAll: () -> Unit,
    trackActions: TrackListActions = TrackListActions(),
) {
    when (state) {
        SongsUiState.Loading -> Centered { CircularProgressIndicator() }
        SongsUiState.Error -> Centered {
            Text(stringResource(R.string.songs_error))
            Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.retry)) }
        }
        is SongsUiState.Loaded -> if (state.tracks.isEmpty()) {
            Centered { Text(stringResource(R.string.songs_empty)) }
        } else {
            LazyColumn {
                item {
                    OutlinedButton(
                        onClick = onShuffleAll,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.shuffle_all))
                    }
                }
                itemsIndexed(state.tracks, key = { _, track -> track.id }) { index, track ->
                    SongRow(
                        track = track,
                        onClick = { onSongClick(index) },
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
}

@Composable
internal fun SongRow(
    track: Track,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    isCurrent: Boolean,
    onRemove: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    isDownloaded: Boolean = false,
) {
    val menu = rememberTrackMenu()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .trackClickable(menu, onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        AlbumCover(coverUrl = track.coverUrl, modifier = Modifier.size(44.dp))
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.body1,
                color = if (isCurrent) MaterialTheme.colors.primary else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(
                    artistOrUnknown(track.artist),
                    track.albumTitle,
                ).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isDownloaded) DownloadedMark()
        if (isCurrent) NowPlayingIndicator(modifier = Modifier.padding(end = 8.dp))
        Text(
            text = if (track.durationMs > 0) formatDuration(track.durationMs) else "",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
        )
        TrackActionsMenu(
            menu,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onRemove = onRemove,
            onAddToPlaylist = onAddToPlaylist,
        )
    }
}

@Composable
fun SongsRoute(viewModel: SongsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    SongsScreen(
        state = state,
        onRetry = viewModel::retry,
        onSongClick = viewModel::playFrom,
        onShuffleAll = viewModel::shuffleAll,
        trackActions = trackListActions(),
    )
}
