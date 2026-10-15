package org.jellyfin.mobile.music.library

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.common.ui.components.DownloadedMark
import org.jellyfin.mobile.common.ui.components.EmptyState
import org.jellyfin.mobile.common.ui.components.ErrorState
import org.jellyfin.mobile.common.ui.components.LoadingState
import org.jellyfin.mobile.common.ui.components.MediaListRow
import org.jellyfin.mobile.common.ui.components.SecondaryText
import org.jellyfin.mobile.common.ui.theme.VaultSpacing
import org.jellyfin.mobile.music.R
import org.jellyfin.mobile.music.domain.Track
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
        SongsUiState.Loading -> LoadingState()
        SongsUiState.Error -> ErrorState(stringResource(R.string.songs_error), onRetry = onRetry)
        is SongsUiState.Loaded -> if (state.tracks.isEmpty()) {
            EmptyState(stringResource(R.string.songs_empty))
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
    MediaListRow(
        title = track.title,
        subtitle = listOf(
            artistOrUnknown(track.artist),
            track.albumTitle,
        ).filter { it.isNotBlank() }.joinToString(" · "),
        cover = { AlbumCover(coverUrl = track.coverUrl) },
        highlighted = isCurrent,
        modifier = Modifier.trackClickable(menu, onClick),
    ) {
        if (isDownloaded) DownloadedMark()
        if (isCurrent) NowPlayingIndicator(modifier = Modifier.padding(end = VaultSpacing.Small))
        SecondaryText(
            text = if (track.durationMs > 0) formatDuration(track.durationMs) else "",
            style = MaterialTheme.typography.body2,
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
