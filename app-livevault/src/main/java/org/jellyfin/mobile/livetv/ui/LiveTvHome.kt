package org.jellyfin.mobile.livetv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.livetv.R

/** The signed-in app. The channel browser (M2) replaces this placeholder. */
@Composable
fun LiveTvHome() {
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
        Text(stringResource(R.string.live_tv_title), style = MaterialTheme.typography.h4)
    }
}
