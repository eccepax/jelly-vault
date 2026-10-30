package org.jellyfin.mobile.common.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.common.R
import org.jellyfin.mobile.common.ui.VaultTestTags
import org.jellyfin.mobile.common.ui.theme.VaultAlpha
import org.jellyfin.mobile.common.ui.theme.VaultSpacing

/**
 * The bar along the bottom of a library while something is loaded: what it is, play/pause, and
 * any [actions] the app adds (next, stop). Tapping the bar opens the full player.
 */
// Each is one thing the bar shows or does; grouping them would only hide them from the callers
@Suppress("LongParameterList")
@Composable
fun MiniPlayerBar(
    title: String,
    subtitle: String?,
    cover: @Composable () -> Unit,
    isPlaying: Boolean,
    /** No play button when null: for what can't pause (a live channel). */
    onTogglePlayPause: (() -> Unit)?,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    /** How far through it is, from 0 to 1; no bar when null. */
    progress: Float? = null,
    /** Waiting for the stream (see [PlayPauseButton]). */
    isLoading: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        color = MaterialTheme.colors.surface,
        elevation = MINI_PLAYER_ELEVATION,
        modifier = modifier.testTag(VaultTestTags.MINI_PLAYER),
    ) {
        Column {
            if (progress != null) LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpand)
                    .padding(horizontal = VaultSpacing.Medium, vertical = VaultSpacing.Small),
            ) {
                Box(modifier = Modifier.size(MINI_PLAYER_COVER_SIZE)) { cover() }
                Column(modifier = Modifier.weight(1f).padding(horizontal = VaultSpacing.Medium)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.subtitle2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = VaultAlpha.SECONDARY),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (onTogglePlayPause != null) {
                    PlayPauseButton(isPlaying = isPlaying, onClick = onTogglePlayPause, isLoading = isLoading)
                }
                actions()
            }
        }
    }
}

/**
 * Play or pause, saying (and showing) which tapping it does. While [isLoading] (asked to play, the
 * stream not there yet: a cast device starting a song, say) a ring turns round it, so a tap that
 * seems to do nothing isn't taken for one that didn't register.
 */
@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Int = 24,
    isLoading: Boolean = false,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        if (isLoading) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size((iconSize + LOADING_RING_MARGIN).dp),
            )
        }
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = stringResource(if (isPlaying) R.string.vault_pause else R.string.vault_play),
            modifier = Modifier.size(iconSize.dp),
        )
    }
}

/** How much wider than the icon the loading ring is. */
private const val LOADING_RING_MARGIN = 12

private val MINI_PLAYER_ELEVATION = 8.dp
private val MINI_PLAYER_COVER_SIZE = 44.dp
