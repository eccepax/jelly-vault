package org.jellyfin.mobile.music.downloads

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadManager
import org.jellyfin.mobile.common.downloads.VaultDownloadService
import org.jellyfin.mobile.music.R
import org.koin.android.ext.android.inject

/** Keeps song downloads going in the background (see [VaultDownloadService]). */
@UnstableApi
class TuneDownloadService : VaultDownloadService(R.string.downloads_channel) {
    private val manager: DownloadManager by inject()

    override fun getDownloadManager(): DownloadManager = manager

    override fun progressTitle(active: Int): String =
        resources.getQuantityString(R.plurals.downloading_songs, active, active)
}
