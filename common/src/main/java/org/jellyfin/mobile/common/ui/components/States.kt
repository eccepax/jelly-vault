package org.jellyfin.mobile.common.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.common.R
import org.jellyfin.mobile.common.ui.theme.VaultAlpha
import org.jellyfin.mobile.common.ui.theme.VaultSpacing

/** A screen waiting for its content. */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Centered(modifier) { CircularProgressIndicator() }
}

/** A screen with nothing to show yet, saying why. */
@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Centered(modifier) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colors.onBackground.copy(alpha = VaultAlpha.SECONDARY),
                modifier = Modifier.padding(bottom = VaultSpacing.Large).size(STATE_ICON_SIZE),
            )
        }
        Text(
            message,
            color = MaterialTheme.colors.onBackground.copy(alpha = VaultAlpha.SECONDARY),
            textAlign = TextAlign.Center,
        )
    }
}

/** A screen whose content didn't load, offering to try again. */
@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Centered(modifier) {
        Text(message, textAlign = TextAlign.Center)
        Button(onClick = onRetry, modifier = Modifier.padding(top = VaultSpacing.Large)) {
            Text(stringResource(R.string.vault_retry))
        }
    }
}

/** Shown while the Vault can't be reached and the app is working from its downloads. */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier, text: String = stringResource(R.string.vault_offline_banner)) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.surface)
            .padding(horizontal = VaultSpacing.Large, vertical = VaultSpacing.Small),
    ) {
        Icon(
            Icons.Filled.CloudOff,
            contentDescription = null,
            modifier = Modifier.padding(end = VaultSpacing.Small).size(MARK_SIZE),
        )
        Text(text, style = MaterialTheme.typography.caption)
    }
}

/** The small tick on an item that's downloaded to the device. */
@Composable
fun DownloadedMark(modifier: Modifier = Modifier) {
    Icon(
        Icons.Filled.DownloadDone,
        contentDescription = stringResource(R.string.vault_downloaded),
        tint = MaterialTheme.colors.primary,
        modifier = modifier.padding(end = VaultSpacing.Small).size(MARK_SIZE),
    )
}

@Composable
private fun Centered(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(VaultSpacing.XLarge),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

private val STATE_ICON_SIZE = 64.dp
private val MARK_SIZE = 16.dp
