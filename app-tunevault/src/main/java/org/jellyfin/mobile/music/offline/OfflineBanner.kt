package org.jellyfin.mobile.music.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.music.R

/** Shown above the tabs while the Vault can't be reached. */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.surface)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Icon(Icons.Filled.CloudOff, contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(16.dp))
        Text(stringResource(R.string.offline_banner), style = MaterialTheme.typography.caption)
    }
}
