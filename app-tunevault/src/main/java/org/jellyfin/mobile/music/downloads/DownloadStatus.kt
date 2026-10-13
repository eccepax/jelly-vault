package org.jellyfin.mobile.music.downloads

import org.jellyfin.mobile.music.domain.Track

/** How far along a downloaded album or playlist is, as a whole. */
sealed interface DownloadStatus {
    data object NotDownloaded : DownloadStatus
    data class InProgress(val done: Int, val total: Int) : DownloadStatus

    /** Some songs are held back, e.g. waiting for Wi-Fi. */
    data class Waiting(val done: Int, val total: Int) : DownloadStatus
    data class Failed(val failed: Int, val total: Int) : DownloadStatus
    data object Downloaded : DownloadStatus
}

/** An album or playlist kept on the device, with its songs in order. */
data class DownloadedCollection(
    val id: String,
    val kind: Kind,
    val title: String,
    val artist: String?,
    val coverUrl: String?,
    val tracks: List<Track>,
) {
    enum class Kind { ALBUM, PLAYLIST }
}
