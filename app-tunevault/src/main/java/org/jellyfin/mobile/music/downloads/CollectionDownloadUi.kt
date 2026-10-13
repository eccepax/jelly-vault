package org.jellyfin.mobile.music.downloads

/** What an album or playlist page needs to show and change its download. */
data class CollectionDownloadUi(
    val status: DownloadStatus = DownloadStatus.NotDownloaded,
    val onDownload: () -> Unit = {},
    val onRemove: () -> Unit = {},
    val onRetry: () -> Unit = {},
)
