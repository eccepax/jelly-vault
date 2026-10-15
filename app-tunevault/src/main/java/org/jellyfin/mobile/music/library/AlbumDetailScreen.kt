package org.jellyfin.mobile.music.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.common.ui.components.DownloadedMark
import org.jellyfin.mobile.common.ui.components.EmptyState
import org.jellyfin.mobile.common.ui.components.ErrorState
import org.jellyfin.mobile.common.ui.components.LoadingState
import org.jellyfin.mobile.common.ui.components.VaultTopBar
import org.jellyfin.mobile.common.ui.theme.VaultAlpha
import org.jellyfin.mobile.music.R
import org.jellyfin.mobile.music.domain.Track
import org.jellyfin.mobile.music.downloads.CollectionDownloadUi
import org.jellyfin.mobile.music.downloads.DownloadButton
import org.jellyfin.mobile.music.downloads.rememberAskForNotificationsOnce
import org.jellyfin.mobile.music.ui.artistOrUnknown
import org.jellyfin.mobile.music.ui.formatDuration
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AlbumDetailScreen(
    state: AlbumDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onTrackClick: (index: Int) -> Unit,
    trackActions: TrackListActions = TrackListActions(),
    favorite: FavoriteUi = FavoriteUi(),
    download: CollectionDownloadUi = CollectionDownloadUi(),
) {
    Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.systemBarsPadding()) {
            VaultTopBar(
                title = "",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
            when (state) {
                AlbumDetailUiState.Loading -> LoadingState(Modifier.testTag(AlbumDetailScreenTags.LOADING))
                AlbumDetailUiState.NotFound -> EmptyState(stringResource(R.string.album_not_found))
                AlbumDetailUiState.Error -> ErrorState(stringResource(R.string.album_error), onRetry = onRetry)
                is AlbumDetailUiState.Loaded -> AlbumContent(
                    state = state,
                    onPlay = onPlay,
                    onShuffle = onShuffle,
                    onTrackClick = onTrackClick,
                    trackActions = trackActions,
                    favorite = favorite,
                    download = download,
                )
            }
        }
    }
}

@Composable
private fun AlbumContent(
    state: AlbumDetailUiState.Loaded,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onTrackClick: (index: Int) -> Unit,
    trackActions: TrackListActions,
    favorite: FavoriteUi,
    download: CollectionDownloadUi,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item { AlbumHeader(state, onPlay, onShuffle, favorite, download) }
        itemsIndexed(state.tracks, key = { _, track -> track.id }) { index, track ->
            TrackRow(
                number = track.trackNumber ?: index + 1,
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

@Composable
private fun AlbumHeader(
    state: AlbumDetailUiState.Loaded,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    favorite: FavoriteUi,
    download: CollectionDownloadUi,
) {
    val album = state.album
    val trackCount = pluralStringResource(R.plurals.track_count, state.tracks.size, state.tracks.size)
    val details = listOfNotNull(album.year?.toString(), trackCount, formatDuration(state.totalDurationMs))
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        AlbumCover(coverUrl = album.coverUrl, modifier = Modifier.width(200.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.h6,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(text = artistOrUnknown(album.artist), style = MaterialTheme.typography.subtitle1)
        Text(
            text = details.joinToString(" · "),
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onBackground.copy(alpha = VaultAlpha.SECONDARY),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            FavoriteButton(isFavorite = favorite.isFavorite, onToggle = favorite.onToggle)
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
        DownloadButton(download, modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun TrackRow(
    number: Int,
    track: Track,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    isCurrent: Boolean,
    onAddToPlaylist: (() -> Unit)? = null,
    isDownloaded: Boolean = false,
) {
    val menu = rememberTrackMenu()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .trackClickable(menu, onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (isCurrent) {
            NowPlayingIndicator(modifier = Modifier.width(32.dp).size(18.dp))
        } else {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onBackground.copy(alpha = VaultAlpha.SECONDARY),
                modifier = Modifier.width(32.dp),
            )
        }
        Text(
            text = track.title,
            style = MaterialTheme.typography.body1,
            color = if (isCurrent) MaterialTheme.colors.primary else Color.Unspecified,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isDownloaded) DownloadedMark()
        Text(
            text = if (track.durationMs > 0) formatDuration(track.durationMs) else "",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onBackground.copy(alpha = VaultAlpha.SECONDARY),
            modifier = Modifier.padding(start = 12.dp),
        )
        TrackActionsMenu(menu, onPlayNext = onPlayNext, onAddToQueue = onAddToQueue, onAddToPlaylist = onAddToPlaylist)
    }
}

@Composable
fun AlbumDetailRoute(albumId: String, onBack: () -> Unit) {
    val viewModel: AlbumDetailViewModel = koinViewModel(key = albumId) { parametersOf(albumId) }
    val state by viewModel.state.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val downloadStatus by viewModel.downloadStatus.collectAsState()
    val askForNotifications = rememberAskForNotificationsOnce()
    AlbumDetailScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onPlay = viewModel::playAll,
        onShuffle = viewModel::shuffle,
        onTrackClick = viewModel::playTrack,
        trackActions = trackListActions(),
        favorite = FavoriteUi(isFavorite, viewModel::toggleFavorite),
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
