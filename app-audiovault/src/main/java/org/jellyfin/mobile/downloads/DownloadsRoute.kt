package org.jellyfin.mobile.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import org.jellyfin.mobile.R
import org.jellyfin.mobile.common.ui.components.ConfirmDialog
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.library.CoverUrl
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** What's downloaded: tapping one plays its book from there; holding one offers to remove it. */
@Composable
fun DownloadsRoute(
    onBack: () -> Unit,
    onPlay: (PlayOptions) -> Unit,
    viewModel: DownloadsViewModel = koinViewModel(),
    coverUrl: CoverUrl = koinInject(),
) {
    val downloads by viewModel.downloads.collectAsState()
    val rows = rows(downloads, coverUrl)
    var removing by remember { mutableStateOf<DownloadRow?>(null) }
    DownloadsScreen(
        rows = rows,
        actions = DownloadsActions(
            onBack = onBack,
            onPlay = { onPlay(playOptions(it, downloads)) },
            onRemove = { download -> removing = rows.find { it.download == download } },
        ),
    )
    removing?.let { row ->
        ConfirmDialog(
            title = stringResource(R.string.confirm_deletion),
            message = stringResource(R.string.confirm_deletion_desc, row.title),
            confirmLabel = stringResource(R.string.yes),
            onConfirm = { viewModel.remove(row.download) },
            onDismiss = { removing = null },
        )
    }
}

@Composable
private fun rows(downloads: List<DownloadEntity>, coverUrl: CoverUrl): List<DownloadRow> {
    val context = LocalContext.current
    val coverSize = dimensionResource(R.dimen.movie_thumbnail_list_size).toPx()
    return remember(downloads, coverSize) {
        downloads.map { download ->
            val item = download.mediaSource.item
            DownloadRow(
                download = download,
                title = download.mediaSource.getName(context),
                subtitle = listOfNotNull(item?.albumArtist, download.fileSize).joinToString(" · "),
                coverUrl = coverUrl(download.mediaSource.itemId, coverSize),
            )
        }
    }
}

@Composable
private fun Dp.toPx(): Int = with(LocalDensity.current) { roundToPx() }

/** Plays [download]'s whole downloaded book, starting at [download]. */
private fun playOptions(download: DownloadEntity, downloads: List<DownloadEntity>): PlayOptions {
    val book = downloadedBookOf(download, downloads)
    return PlayOptions(
        ids = book.map { it.mediaSource.itemId },
        mediaSourceId = download.mediaSource.id,
        startIndex = book.indexOf(download).coerceAtLeast(0),
        startPosition = null,
        audioStreamIndex = 1,
        subtitleStreamIndex = -1,
        playFromDownloads = true,
    )
}
