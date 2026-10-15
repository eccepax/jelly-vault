package org.jellyfin.mobile.music.downloads

import org.jellyfin.mobile.common.downloads.GroupStatus
/** What an album or playlist page needs to show and change its download. */
data class CollectionDownloadUi(
    val status: GroupStatus = GroupStatus.NotDownloaded,
    val onDownload: () -> Unit = {},
    val onRemove: () -> Unit = {},
    val onRetry: () -> Unit = {},
)
