package org.jellyfin.mobile.music.playlists

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
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.music.R
import org.jellyfin.mobile.music.domain.Playlist
import org.jellyfin.mobile.music.library.AlbumCover
import org.jellyfin.mobile.music.library.Centered
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PlaylistsScreen(
    state: PlaylistsUiState,
    onRetry: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onCreate: (name: String) -> Unit,
) {
    Column {
        when (state) {
            PlaylistsUiState.Loading -> Centered { CircularProgressIndicator() }
            PlaylistsUiState.Error -> Centered {
                Text(stringResource(R.string.playlists_error))
                Button(
                    onClick = onRetry,
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text(stringResource(R.string.retry)) }
            }
            is PlaylistsUiState.Loaded -> LazyColumn {
                item { NewPlaylist(onCreate = onCreate) }
                if (state.playlists.isEmpty()) {
                    item {
                        Text(stringResource(R.string.playlists_empty), modifier = Modifier.padding(16.dp))
                    }
                }
                items(state.playlists, key = { it.id }) { playlist ->
                    PlaylistRow(playlist = playlist, onClick = { onPlaylistClick(playlist) })
                }
            }
        }
    }
}

/** "New playlist", which opens a name field in place (not a dialog); Create needs a name. */
@Composable
fun NewPlaylist(onCreate: (name: String) -> Unit, modifier: Modifier = Modifier) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    if (!editing) {
        NewPlaylistButton(onClick = { editing = true })
        return
    }
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.playlist_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row {
            TextButton(
                onClick = {
                    onCreate(name)
                    name = ""
                    editing = false
                },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.create)) }
            TextButton(
                onClick = {
                    name = ""
                    editing = false
                },
            ) { Text(stringResource(R.string.cancel)) }
        }
    }
}

@Composable
private fun NewPlaylistButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.new_playlist))
    }
}

@Composable
private fun PlaylistRow(playlist: Playlist, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        AlbumCover(coverUrl = playlist.coverUrl, modifier = Modifier.size(48.dp))
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.body1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(R.plurals.song_count, playlist.trackCount, playlist.trackCount),
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
fun PlaylistsRoute(onPlaylistClick: (Playlist) -> Unit, viewModel: PlaylistsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    PlaylistsScreen(
        state = state,
        onRetry = viewModel::refresh,
        onPlaylistClick = onPlaylistClick,
        onCreate = viewModel::createPlaylist,
    )
}
