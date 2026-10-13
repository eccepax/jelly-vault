package org.jellyfin.mobile.music.nowplaying

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    Surface(color = MaterialTheme.colors.surface, elevation = 8.dp, modifier = modifier.testTag(MiniPlayerTags.ROOT)) {
        Column {
            LinearProgressIndicator(
                progress = state.progress,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpand)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                AlbumCover(coverUrl = track.coverUrl, modifier = Modifier.size(44.dp))
                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.subtitle2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = artistOrUnknown(track.artist),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PlayPauseButton(isPlaying = state.isPlaying, onClick = onTogglePlayPause)
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.next))
                }
            }
        }
    }
}

@Composable
fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, iconSize: Int = 24) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
            modifier = Modifier.size(iconSize.dp),
        )
    }
}
