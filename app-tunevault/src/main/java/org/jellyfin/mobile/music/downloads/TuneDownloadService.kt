package org.jellyfin.mobile.music.downloads

import android.app.Notification
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import org.jellyfin.mobile.music.R
import org.koin.android.ext.android.inject

/**
 * Keeps downloads going with the app in the background, under one progress notification for all
 * songs (never one per song). The scheduler restarts it when a requirement such as Wi-Fi returns.
 */
@UnstableApi
class TuneDownloadService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.downloads_channel,
    0,
) {
    private val manager: DownloadManager by inject()
    private val notifications by lazy { DownloadNotificationHelper(this, CHANNEL_ID) }

    override fun getDownloadManager(): DownloadManager = manager

    override fun getScheduler(): Scheduler = PlatformScheduler(this, JOB_ID)

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification {
        val active = downloads.count { it.state != Download.STATE_COMPLETED }
        return notifications.buildProgressNotification(
            this,
            android.R.drawable.stat_sys_download,
            null,
            resources.getQuantityString(R.plurals.downloading_songs, active, active),
            downloads,
            notMetRequirements,
        )
    }

    private companion object {
        const val NOTIFICATION_ID = 2
        const val JOB_ID = 1
        const val CHANNEL_ID = "downloads"
    }
}
