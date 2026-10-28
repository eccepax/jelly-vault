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
 * the background, with a notification of what's on where that opens the app or ends the cast. It
 * follows [CastHold]: another channel shows in the notification, and once nothing is held it stops
 * itself, always having gone to the foreground first, as Android requires.
 */
class LiveCastService : Service() {
    private val casting: Casting by inject()
    private val show: (Pair<String, String?>?) -> Unit = ::show

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.cast_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        CastHold.listener = show
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_CASTING) {
            // The cast ending frees the stream (see LivePlayback), which then stops a running service.
            // One started just for this (the notification outlived the service) stops again at once.
            casting.stopCasting()
            if (!running) stopSelf(startId)
            return START_NOT_STICKY
        }
        // Every start goes to the foreground, even one whose cast has ended meanwhile
        val held = CastHold.current
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(held),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        running = true
        if (held == null) stop()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (CastHold.listener === show) CastHold.listener = null
        running = false
        super.onDestroy()
    }

    private fun show(held: Pair<String, String?>?) {
        // Not in the foreground yet: it shows what's held once it is
        if (!running) return
        if (held == null) {
            stop()
        } else {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(held))
        }
    }

    private fun stop() {
        running = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(held: Pair<String, String?>?): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            // Back to the activity that's casting, not another on top of it
            Intent(this, LiveMainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, LiveCastService::class.java).setAction(ACTION_STOP_CASTING),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val device = held?.second
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cast_notification)
            .setContentTitle(held?.first.orEmpty())
            .setContentText(device?.let { getString(CastR.string.playing_on, it) })
            .setContentIntent(open)
            .addAction(0, getString(CastR.string.stop_casting), stop)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .build()
    }

    companion object {
        /** Whether a service is in the foreground keeping the app alive, and not stopping. Main thread only. */
        var running = false
            private set

        internal const val NOTIFICATION_ID = 1
        internal const val ACTION_STOP_CASTING = "org.jellyfin.mobile.livetv.STOP_CASTING"
        private const val CHANNEL_ID = "casting"
    }
}
