package org.jellyfin.mobile.downloads

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import org.jellyfin.mobile.R
import org.jellyfin.mobile.common.ui.VaultTestTags
import org.jellyfin.mobile.common.ui.components.CoverArt
import org.jellyfin.mobile.common.ui.components.EmptyState
import org.jellyfin.mobile.common.ui.components.MediaListRow
import org.jellyfin.mobile.common.ui.components.VaultTopBar
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.common.R as CommonR

/** One downloaded track as the list shows it. */
data class DownloadRow(val download: DownloadEntity, val title: String, val subtitle: String?, val coverUrl: String?)

/** What the downloads screen can do. */
data class DownloadsActions(
    val onBack: () -> Unit = {},
    val onPlay: (DownloadEntity) -> Unit = {},
    /** Holding a download: the app asks before removing it. */
    val onRemove: (DownloadEntity) -> Unit = {},
)

@Composable
fun DownloadsScreen(rows: List<DownloadRow>, actions: DownloadsActions) {
    Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.systemBarsPadding()) {
            VaultTopBar(
                title = stringResource(R.string.downloads),
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(CommonR.string.vault_back),
                        )
                    }
                },
            )
            if (rows.isEmpty()) {
                EmptyState(stringResource(R.string.downloads_empty), icon = DownloadPlaceholder)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().testTag(VaultTestTags.LIBRARY_LIST)) {
                    items(rows, key = { it.download.itemId }) { row ->
                        MediaListRow(
                            title = row.title,
                            subtitle = row.subtitle,
                            cover = { CoverArt(url = row.coverUrl, placeholder = DownloadPlaceholder) },
                            modifier = Modifier
                                .testTag(VaultTestTags.LIBRARY_ITEM)
                                .combinedClickable(
                                    onClick = { actions.onPlay(row.download) },
                                    onLongClick = { actions.onRemove(row.download) },
                                ),
                        )
                    }
                }
            }
        }
    }
}

private val DownloadPlaceholder = Icons.Filled.Headphones
