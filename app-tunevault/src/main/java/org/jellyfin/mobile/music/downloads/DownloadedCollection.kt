package org.jellyfin.mobile.music.downloads

import org.jellyfin.mobile.music.domain.Track

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
