package org.jellyfin.mobile.music.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.music.R
import org.jellyfin.mobile.music.domain.RepeatMode
import org.jellyfin.mobile.music.library.AlbumCover
import org.jellyfin.mobile.music.library.FavoriteButton
import org.jellyfin.mobile.music.ui.artistOrUnknown
import org.jellyfin.mobile.music.ui.formatDuration

/** [sleepTimer] is null when the timer is hidden (a setting). */
@Composable
fun NowPlayingScreen(state: NowPlayingUiState, actions: NowPlayingActions, sleepTimer: SleepTimerUi? = SleepTimerUi()) {
    val track = state.track
    Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.systemBarsPadding().padding(horizontal = 24.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = actions.onCollapse) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.collapse))
                }
                Row {
                    sleepTimer?.let { SleepTimerButton(it) }
                    FavoriteButton(isFavorite = state.isFavorite, onToggle = actions.onToggleFavorite)
                    IconButton(onClick = actions.onOpenQueue) {
                        Icon(
                            Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = stringResource(R.string.queue_title),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            AlbumCover(coverUrl = track?.coverUrl, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
            Text(
                text = track?.title.orEmpty(),
                style = MaterialTheme.typography.h6,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = track?.let { artistOrUnknown(it.artist) }.orEmpty(), style = MaterialTheme.typography.subtitle1)
            Text(
                text = track?.albumTitle.orEmpty(),
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(16.dp))
            SeekBar(positionMs = state.positionMs, durationMs = state.durationMs, onSeek = actions.onSeek)
            TransportControls(state, actions)
            sleepTimer?.let { SleepTimerLabel(it.state) }
        }
    }
}

@Composable
private fun TransportControls(state: NowPlayingUiState, actions: NowPlayingActions) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ShuffleButton(isShuffled = state.isShuffled, onClick = actions.onToggleShuffle)
        IconButton(onClick = actions.onPrevious) {
            Icon(
                Icons.Filled.SkipPrevious,
                contentDescription = stringResource(R.string.previous),
                modifier = Modifier.size(36.dp),
            )
        }
        PlayPauseButton(
            isPlaying = state.isPlaying,
            onClick = actions.onTogglePlayPause,
            modifier = Modifier.size(72.dp),
            iconSize = 56,
        )
        IconButton(onClick = actions.onNext) {
            Icon(
                Icons.Filled.SkipNext,
                contentDescription = stringResource(R.string.next),
                modifier = Modifier.size(36.dp),
            )
        }
        RepeatButton(repeatMode = state.repeatMode, onClick = actions.onCycleRepeat)
    }
}

/** Shows a drag's target time while dragging; seeks only when the drag ends. */
@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val playedFraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shownFraction = dragFraction ?: playedFraction
    val seekDescription = stringResource(R.string.seek_bar)
    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = shownFraction,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                dragFraction?.let { onSeek((it * durationMs).toLong()) }
                dragFraction = null
            },
            enabled = durationMs > 0,
            modifier = Modifier.semantics { contentDescription = seekDescription },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val shownPositionMs = dragFraction?.let { (it * durationMs).toLong() } ?: positionMs
            Text(formatDuration(shownPositionMs), style = MaterialTheme.typography.caption)
            Text(formatDuration(durationMs), style = MaterialTheme.typography.caption)
        }
    }
}

@Composable
private fun ShuffleButton(isShuffled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            Icons.Filled.Shuffle,
            contentDescription = stringResource(if (isShuffled) R.string.shuffle_on else R.string.shuffle_off),
            tint = if (isShuffled) MaterialTheme.colors.primary else LocalContentColor.current.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun RepeatButton(repeatMode: RepeatMode, onClick: () -> Unit) {
    val description = when (repeatMode) {
        RepeatMode.OFF -> R.string.repeat_off
        RepeatMode.ALL -> R.string.repeat_all
        RepeatMode.ONE -> R.string.repeat_one
    }
    IconButton(onClick = onClick) {
        Icon(
            if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
            contentDescription = stringResource(description),
            tint = if (repeatMode == RepeatMode.OFF) {
                LocalContentColor.current.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colors.primary
            },
        )
    }
}
