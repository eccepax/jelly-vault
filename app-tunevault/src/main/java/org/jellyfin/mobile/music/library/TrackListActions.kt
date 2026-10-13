package org.jellyfin.mobile.music.library

import androidx.compose.foundation.combinedClickable
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.jellyfin.mobile.music.R
import org.jellyfin.mobile.music.domain.Track

/**
 * What a list of songs needs beyond tapping a song: the long-press queue actions, and which song is
 * playing so its row can be marked. Shared by the album, songs and search lists.
 */
data class TrackListActions(
    val onPlayNext: (Track) -> Unit = {},
    val onAddToQueue: (Track) -> Unit = {},
    val currentTrackId: String? = null,
    /** Offered in the long-press menu when set. */
    val onAddToPlaylist: ((Track) -> Unit)? = null,
    /** Songs on the device, marked on their rows. */
    val downloadedTrackIds: Set<String> = emptySet(),
)

/** Tap to play; long-press (also offered to accessibility services) opens the queue menu. */
fun Modifier.trackClickable(menuOpen: MutableState<Boolean>, onClick: () -> Unit): Modifier =
    combinedClickable(onClick = onClick, onLongClick = { menuOpen.value = true })

@Composable
fun rememberTrackMenu(): MutableState<Boolean> = remember { mutableStateOf(false) }

@Composable
fun TrackActionsMenu(
    menuOpen: MutableState<Boolean>,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onRemove: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
) {
    DropdownMenu(expanded = menuOpen.value, onDismissRequest = { menuOpen.value = false }) {
        DropdownMenuItem(
            onClick = {
                menuOpen.value = false
                onPlayNext()
            },
        ) { Text(stringResource(R.string.play_next)) }
        DropdownMenuItem(
            onClick = {
                menuOpen.value = false
                onAddToQueue()
            },
        ) { Text(stringResource(R.string.add_to_queue)) }
        onAddToPlaylist?.let { add ->
            DropdownMenuItem(
                onClick = {
                    menuOpen.value = false
                    add()
                },
            ) { Text(stringResource(R.string.add_to_playlist)) }
        }
        onRemove?.let { remove ->
            DropdownMenuItem(
                onClick = {
                    menuOpen.value = false
                    remove()
                },
            ) { Text(stringResource(R.string.remove_from_playlist)) }
        }
    }
}

/** Marks the row of the song that's playing. */
@Composable
fun NowPlayingIndicator(modifier: Modifier = Modifier) {
    Icon(
        Icons.Filled.GraphicEq,
        contentDescription = stringResource(R.string.now_playing),
        tint = MaterialTheme.colors.primary,
        modifier = modifier,
    )
}
