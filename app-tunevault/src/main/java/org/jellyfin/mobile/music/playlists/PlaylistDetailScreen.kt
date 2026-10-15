package org.jellyfin.mobile.music.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.common.ui.components.EmptyState
import org.jellyfin.mobile.common.ui.components.LoadingState
import org.jellyfin.mobile.common.ui.components.VaultTopBar
import org.jellyfin.mobile.common.ui.theme.VaultAlpha
import org.jellyfin.mobile.music.R
import org.jellyfin.mobile.music.downloads.CollectionDownloadUi
import org.jellyfin.mobile.music.downloads.DownloadButton
import org.jellyfin.mobile.music.downloads.rememberAskForNotificationsOnce
import org.jellyfin.mobile.music.library.SongRow
import org.jellyfin.mobile.music.library.TrackListActions
import org.jellyfin.mobile.music.library.trackListActions
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun PlaylistDetailScreen(
    state: PlaylistDetailUiState,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onTrackClick: (index: Int) -> Unit,
    onRemove: (index: Int) -> Unit,
    trackActions: TrackListActions = TrackListActions(),
    download: CollectionDownloadUi = CollectionDownloadUi(),
) {
    Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.systemBarsPadding()) {
            VaultTopBar(
                title = if (state is PlaylistDetailUiState.Loaded) state.playlist.name else "",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
            when (state) {
                PlaylistDetailUiState.Loading -> LoadingState()
                PlaylistDetailUiState.NotFound -> EmptyState(stringResource(R.string.playlist_not_found))
                PlaylistDetailUiState.Error -> EmptyState(stringResource(R.string.playlist_error))
                is PlaylistDetailUiState.Loaded -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    item { Header(state, onPlay, onShuffle, download) }
                    itemsIndexed(state.entries, key = { index, entry -> "${entry.entryId}:$index" }) { index, entry ->
                        SongRow(
                            track = entry.track,
                            onClick = { onTrackClick(index) },
                            onPlayNext = { trackActions.onPlayNext(entry.track) },
                            onAddToQueue = { trackActions.onAddToQueue(entry.track) },
                            isCurrent = entry.track.id == trackActions.currentTrackId,
                            onRemove = { onRemove(index) },
                            onAddToPlaylist = trackActions.onAddToPlaylist?.let { add -> { add(entry.track) } },
                            isDownloaded = entry.track.id in trackActions.downloadedTrackIds,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    state: PlaylistDetailUiState.Loaded,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    download: CollectionDownloadUi,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = pluralStringResource(R.plurals.song_count, state.entries.size, state.entries.size),
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onBackground.copy(alpha = VaultAlpha.SECONDARY),
        )
        if (state.entries.isEmpty()) {
            Text(stringResource(R.string.playlist_empty), modifier = Modifier.padding(top = 16.dp))
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
            Button(onClick = onPlay) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.play))
            }
            OutlinedButton(onClick = onShuffle) {
                Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.shuffle))
            }
        }
        DownloadButton(download, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun PlaylistDetailRoute(playlistId: String, onBack: () -> Unit) {
    val viewModel: PlaylistDetailViewModel = koinViewModel(key = "playlist:$playlistId") { parametersOf(playlistId) }
    val state by viewModel.state.collectAsState()
    val downloadStatus by viewModel.downloadStatus.collectAsState()
    val askForNotifications = rememberAskForNotificationsOnce()
    PlaylistDetailScreen(
        state = state,
        onBack = onBack,
        onPlay = viewModel::playAll,
        onShuffle = viewModel::shuffle,
        onTrackClick = viewModel::playTrack,
        onRemove = viewModel::remove,
        trackActions = trackListActions(),
        download = CollectionDownloadUi(
            status = downloadStatus,
            onDownload = {
                askForNotifications()
                viewModel.download()
            },
            onRemove = viewModel::removeDownload,
            onRetry = viewModel::retryDownload,
        ),
    )
}
