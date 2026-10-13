package org.jellyfin.mobile.player

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import org.jellyfin.mobile.utils.AndroidVersion
import org.jellyfin.mobile.utils.Constants

class AudioPlaybackService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        @Suppress("DEPRECATION")
        val notification = intent?.getParcelableExtra<Notification>(EXTRA_NOTIFICATION)
        if (notification != null) {
            if (AndroidVersion.isAtLeastQ) {
                startForeground(
                    Constants.PLAYER_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                startForeground(Constants.PLAYER_NOTIFICATION_ID, notification)
            }
        } else {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (AndroidVersion.isAtLeastN) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        private const val EXTRA_NOTIFICATION = "notification"

        fun start(context: Context, notification: Notification) {
            val intent = Intent(context, AudioPlaybackService::class.java)
                .putExtra(EXTRA_NOTIFICATION, notification)
            if (AndroidVersion.isAtLeastO) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AudioPlaybackService::class.java))
        }
    }
}
