package org.jellyfin.mobile.downloads

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadManager
import org.jellyfin.mobile.R
import org.jellyfin.mobile.common.downloads.VaultDownloadService
import org.koin.android.ext.android.inject

/** Keeps audiobook downloads going in the background (see [VaultDownloadService]). */
@UnstableApi
class AudioDownloadService : VaultDownloadService(R.string.downloads) {
    private val manager: DownloadManager by inject()

    override fun getDownloadManager(): DownloadManager = manager

    override fun progressTitle(active: Int): String =
        resources.getQuantityString(R.plurals.downloading_tracks, active, active)
}
