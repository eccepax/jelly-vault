package org.jellyfin.mobile.music.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.music.R

@Composable
fun DownloadButton(ui: CollectionDownloadUi, modifier: Modifier = Modifier) =
    DownloadButton(ui.status, ui.onDownload, ui.onRemove, ui.onRetry, modifier)

/**
 * An album or playlist's download, labelled with how far it has got. Removing (or cancelling) goes
 * through a menu, so a stray tap never deletes anything.
 */
@Composable
fun DownloadButton(
    status: DownloadStatus,
    onDownload: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val label = labelFor(status)
    val description = if (status is DownloadStatus.Failed) stringResource(R.string.retry_download) else label
    Box(modifier = modifier) {
        TextButton(
            modifier = Modifier.semantics { contentDescription = description },
            onClick = {
                when (status) {
                    DownloadStatus.NotDownloaded -> onDownload()
                    is DownloadStatus.Failed -> onRetry()
                    else -> menuOpen = true
                }
            },
        ) {
            when (status) {
                is DownloadStatus.InProgress -> CircularProgressIndicator(
                    progress = if (status.total == 0) 0f else status.done.toFloat() / status.total,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
                else -> Icon(iconFor(status), contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                color = if (status is DownloadStatus.Failed) MaterialTheme.colors.error else MaterialTheme.colors.primary,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                onClick = {
                    menuOpen = false
                    onRemove()
                },
            ) {
                Text(
                    stringResource(
                        if (status == DownloadStatus.Downloaded) R.string.remove_download else R.string.cancel_download,
                    ),
                )
            }
        }
    }
}

private fun iconFor(status: DownloadStatus) = when (status) {
    DownloadStatus.Downloaded -> Icons.Filled.DownloadDone
    is DownloadStatus.Failed -> Icons.Filled.ErrorOutline
    is DownloadStatus.Waiting -> Icons.Filled.WifiOff
    else -> Icons.Filled.Download
}

/** How far a download has got, in words. */
@Composable
fun statusText(status: DownloadStatus): String = labelFor(status)

@Composable
private fun labelFor(status: DownloadStatus): String = when (status) {
    DownloadStatus.NotDownloaded -> stringResource(R.string.download)
    DownloadStatus.Downloaded -> stringResource(R.string.downloaded)
    is DownloadStatus.InProgress -> stringResource(R.string.downloading_progress, status.done, status.total)
    is DownloadStatus.Waiting -> stringResource(R.string.waiting_for_wifi, status.done, status.total)
    is DownloadStatus.Failed -> pluralStringResource(R.plurals.download_failed, status.failed, status.failed)
}
