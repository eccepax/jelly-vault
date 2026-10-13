package org.jellyfin.mobile.music.playlists

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.music.R
import org.koin.compose.viewmodel.koinViewModel

/** Full screen rather than a dialog, so it behaves like every other overlay in the app. */
@Composable
fun AddToPlaylistScreen(
    state: AddToPlaylistUiState.Picking,
    onPick: (playlistId: String) -> Unit,
    onCreate: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.systemBarsPadding()) {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.add_to_playlist_title, state.track.title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                    }
                },
            )
            LazyColumn {
                item { NewPlaylist(onCreate = onCreate) }
                items(state.playlists, key = { it.id }) { playlist ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(playlist.id) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(playlist.name, style = MaterialTheme.typography.body1)
                        Text(
                            text = pluralStringResource(R.plurals.song_count, playlist.trackCount, playlist.trackCount),
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shows the picker over everything when a song row asks for it, and a brief confirmation after.
 * Composed once, at the top of the signed-in app.
 */
@Composable
fun AddToPlaylistHost(viewModel: AddToPlaylistViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val result by viewModel.lastResult.collectAsState()
    val context = LocalContext.current
    val message = result?.let { messageFor(it) }
    LaunchedEffect(result) {
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.resultShown()
        }
    }
    (state as? AddToPlaylistUiState.Picking)?.let { picking ->
        BackHandler(onBack = viewModel::dismiss)
        AddToPlaylistScreen(
            state = picking,
            onPick = viewModel::addTo,
            onCreate = viewModel::createWith,
            onDismiss = viewModel::dismiss,
        )
    }
}

@Composable
private fun messageFor(result: AddToPlaylistResult): String = when (result) {
    is AddToPlaylistResult.Added -> stringResource(R.string.added_to_playlist, result.playlistName)
    is AddToPlaylistResult.AlreadyThere -> stringResource(R.string.already_in_playlist, result.playlistName)
    is AddToPlaylistResult.Created -> stringResource(R.string.added_to_playlist, result.playlistName)
    AddToPlaylistResult.Failed -> stringResource(R.string.add_to_playlist_failed)
}
