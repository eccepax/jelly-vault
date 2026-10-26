package org.jellyfin.mobile.cast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** "Playing on <device>" under a player's controls while casting; nothing while playing here. */
@Composable
fun PlayingOnLabel(castDevice: String?, modifier: Modifier = Modifier) {
    if (castDevice == null) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        Icon(
            Icons.Filled.CastConnected,
            contentDescription = null,
            tint = MaterialTheme.colors.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(R.string.playing_on, castDevice),
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.primary,
        )
    }
}
