package org.jellyfin.mobile.livetv.channels

import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.jellyfin.mobile.common.ui.components.MiniPlayerBar
import org.jellyfin.mobile.livetv.R
import org.jellyfin.mobile.livetv.domain.LiveChannel
import org.jellyfin.mobile.cast.R as CastR

/**
 * Along the bottom of the channel list, the [channel] left playing on [castDevice] (null until it
 * has said its name): tapping it goes back to the player, its button stops the channel. A live
 * channel doesn't pause, so there's no play button.
 */
@Composable
fun CastStrip(
    channel: LiveChannel,
    castDevice: String?,
    onOpen: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    failed: Boolean = false,
) {
    val device = castDevice ?: stringResource(CastR.string.unnamed_cast_device)
    val subtitle = when {
        failed -> stringResource(R.string.player_failed)
        else -> stringResource(CastR.string.playing_on, device)
    }
    MiniPlayerBar(
        title = channel.castTitle,
        subtitle = subtitle,
        cover = { ChannelLogo(channel.logoUrl) },
        isPlaying = !failed,
        onTogglePlayPause = null,
        onExpand = onOpen,
        modifier = modifier,
        actions = {
            IconButton(onClick = onStop) {
                Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.stop_channel))
            }
        },
    )
}
