package org.jellyfin.mobile.music.nowplaying

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.jellyfin.mobile.common.ui.components.MiniPlayerBar
import org.jellyfin.mobile.music.R
import org.jellyfin.mobile.music.library.AlbumCover
import org.jellyfin.mobile.music.ui.artistOrUnknown

/** The bar along the bottom of the library while something is loaded. Renders nothing otherwise. */
@Composable
fun MiniPlayer(
    state: NowPlayingUiState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.track ?: return
    MiniPlayerBar(
        title = track.title,
        subtitle = artistOrUnknown(track.artist),
        cover = { AlbumCover(coverUrl = track.coverUrl, modifier = Modifier.fillMaxSize()) },
        isPlaying = state.isPlaying,
        onTogglePlayPause = onTogglePlayPause,
        onExpand = onExpand,
        progress = state.progress,
        modifier = modifier,
    ) {
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.next))
        }
    }
}
