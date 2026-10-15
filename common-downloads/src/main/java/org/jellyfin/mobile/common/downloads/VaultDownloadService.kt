package org.jellyfin.mobile.common.downloads

import android.app.Notification
import androidx.annotation.StringRes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler

/**
 * Keeps an app's downloads going in the background, under one progress notification for all of them
 * (never one per item). The scheduler restarts it when a requirement such as Wi-Fi returns. An app
 * subclasses it, supplies its DownloadManager and a title, and declares it in its manifest along
 * with Media3's PlatformSchedulerService.
 */
@UnstableApi
abstract class VaultDownloadService(
    @StringRes channelName: Int,
) : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    channelName,
    0,
) {
    private val notifications by lazy { DownloadNotificationHelper(this, CHANNEL_ID) }

    /** The notification's title while [active] items are still downloading. */
    abstract fun progressTitle(active: Int): String

    override fun getScheduler(): Scheduler = PlatformScheduler(this, JOB_ID)

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification {
        val active = downloads.count { it.state != Download.STATE_COMPLETED }
        return notifications.buildProgressNotification(
            this,
            android.R.drawable.stat_sys_download,
            null,
            progressTitle(active),
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
