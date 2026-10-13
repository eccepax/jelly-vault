package org.jellyfin.mobile.music.downloads

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.music.R

/** The small tick on a song row that's on the device. */
@Composable
fun DownloadedMark(modifier: Modifier = Modifier) {
    Icon(
        Icons.Filled.DownloadDone,
        contentDescription = stringResource(R.string.downloaded),
        tint = MaterialTheme.colors.primary,
        modifier = modifier.padding(end = 8.dp).size(16.dp),
    )
}
