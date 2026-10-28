package org.jellyfin.mobile.livetv.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import org.jellyfin.mobile.cast.Casting
import org.jellyfin.mobile.livetv.LiveMainActivity
import org.jellyfin.mobile.livetv.R
import org.koin.android.ext.android.inject
import org.jellyfin.mobile.cast.R as CastR

/**
 * Runs while a channel plays on a cast device (see [ServiceCastKeepAlive]), so the app carries on in
 * the background, with a notification of what's on where that opens the app or ends the cast.
 */
class LiveCastService : Service() {
    private val casting: Casting by inject()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_CASTING) {
            // The cast ending frees the stream (see LivePlayback), which then stops this service
            casting.stopCasting()
            return START_NOT_STICKY
        }
        val channel = intent?.getStringExtra(EXTRA_CHANNEL).orEmpty()
        val device = intent?.getStringExtra(EXTRA_DEVICE)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(channel, device),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        return START_NOT_STICKY
    }

    private fun notification(channel: String, device: String?): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.cast_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, LiveMainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, LiveCastService::class.java).setAction(ACTION_STOP_CASTING),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cast_notification)
            .setContentTitle(channel)
            .setContentText(device?.let { getString(CastR.string.playing_on, it) })
            .setContentIntent(open)
            .addAction(0, getString(CastR.string.stop_casting), stop)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .build()
    }

    companion object {
        const val EXTRA_CHANNEL = "channel"
        const val EXTRA_DEVICE = "device"
        private const val ACTION_STOP_CASTING = "org.jellyfin.mobile.livetv.STOP_CASTING"
        private const val CHANNEL_ID = "casting"
        private const val NOTIFICATION_ID = 1
    }
}
