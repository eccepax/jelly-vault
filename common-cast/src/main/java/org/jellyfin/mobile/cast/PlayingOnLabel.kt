package org.jellyfin.mobile.cast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.common.ui.components.ConfirmDialog

/**
 * "Playing on <device>" under a player's controls while casting; nothing while playing here.
 * Tapping it offers to stop casting ([onStopCasting]).
 */
@Composable
fun PlayingOnLabel(castDevice: String?, onStopCasting: () -> Unit, modifier: Modifier = Modifier) {
    if (castDevice == null) return
    // Kept through rotation, like the other confirmations
    var confirming by rememberSaveable { mutableStateOf(false) }
    val playingOn = stringResource(R.string.playing_on, castDevice)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clickable { confirming = true }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Icon(
            Icons.Filled.CastConnected,
            contentDescription = null,
            tint = MaterialTheme.colors.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(text = playingOn, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.primary)
    }
    if (confirming) {
        ConfirmDialog(
            message = playingOn,
            confirmLabel = stringResource(R.string.stop_casting),
            onConfirm = onStopCasting,
            onDismiss = { confirming = false },
        )
    }
}
