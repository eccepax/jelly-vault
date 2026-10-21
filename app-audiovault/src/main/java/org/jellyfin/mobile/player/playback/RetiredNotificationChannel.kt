package org.jellyfin.mobile.player.playback

import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService

/** The channel of AudioVault's own player notification, before Media3 took the notification over. */
internal const val RETIRED_PLAYER_CHANNEL = "org.jellyfin.mobile.media.NOW_PLAYING"

/** Takes channels nothing posts to any more out of an upgraded install's notification settings. */
fun removeRetiredNotificationChannels(context: Context) {
    context.getSystemService<NotificationManager>()?.deleteNotificationChannel(RETIRED_PLAYER_CHANNEL)
}
