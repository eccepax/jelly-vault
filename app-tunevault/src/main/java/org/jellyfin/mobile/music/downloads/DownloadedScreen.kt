package org.jellyfin.mobile.music.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.music.R
import org.jellyfin.mobile.music.library.AlbumCover
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DownloadedScreen(
    items: List<DownloadedItem>,
    onAlbumClick: (albumId: String) -> Unit,
    onPlaylistClick: (playlistId: String) -> Unit,
    onShuffleAll: () -> Unit,
) {
    if (items.isEmpty()) {
        Text(stringResource(R.string.downloads_empty), modifier = Modifier.padding(16.dp))
        return
    }
    LazyColumn {
        item {
            OutlinedButton(onClick = onShuffleAll, modifier = Modifier.padding(16.dp)) {
                Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.shuffle_downloads))
            }
        }
        items(items, key = { it.collection.id }) { item ->
            val collection = item.collection
            DownloadedRow(item) {
                when (collection.kind) {
                    DownloadedCollection.Kind.ALBUM -> onAlbumClick(collection.id)
                    DownloadedCollection.Kind.PLAYLIST -> onPlaylistClick(collection.id)
                }
            }
        }
    }
}

@Composable
private fun DownloadedRow(item: DownloadedItem, onClick: () -> Unit) {
    val collection = item.collection
    val songs = pluralStringResource(R.plurals.song_count, collection.tracks.size, collection.tracks.size)
    val kind = stringResource(
        if (collection.kind == DownloadedCollection.Kind.ALBUM) R.string.kind_album else R.string.kind_playlist,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        AlbumCover(coverUrl = collection.coverUrl, modifier = Modifier.size(56.dp))
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                collection.title,
                style = MaterialTheme.typography.body1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(kind, collection.artist?.takeIf { it.isNotBlank() }, songs).joinToString(" · "),
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.status != DownloadStatus.Downloaded) {
                Text(
                    statusText(item.status),
                    style = MaterialTheme.typography.caption,
                    color = if (item.status is DownloadStatus.Failed) MaterialTheme.colors.error else MaterialTheme.colors.primary,
                )
            }
        }
        if (item.status == DownloadStatus.Downloaded) DownloadedMark()
    }
}

@Composable
fun DownloadedRoute(
    onAlbumClick: (albumId: String) -> Unit,
    onPlaylistClick: (playlistId: String) -> Unit,
    viewModel: DownloadedViewModel = koinViewModel(),
) {
    val items by viewModel.items.collectAsState()
    DownloadedScreen(items, onAlbumClick, onPlaylistClick, viewModel::shuffleAll)
}
