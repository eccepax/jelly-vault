package org.jellyfin.mobile.livetv.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import timber.log.Timber

/**
 * Keeps the app alive while a channel plays on a cast device: in the background nothing else does,
 * and the phone still reports the stream and has to close it (see [LivePlayback]).
 */
interface CastKeepAlive {
    /** [channel] plays on [device]: kept alive, saying so, until [release]. */
    fun hold(channel: String, device: String?)

    fun release()

    /** Nothing kept alive (tests, and where casting can't work). */
    object None : CastKeepAlive {
        override fun hold(channel: String, device: String?) = Unit
        override fun release() = Unit
    }
}

/** [CastKeepAlive] through [LiveCastService], a foreground service with a notification. */
class ServiceCastKeepAlive(private val context: Context) : CastKeepAlive {
    private var holding = false

    override fun hold(channel: String, device: String?) {
        val intent = Intent(context, LiveCastService::class.java)
            .putExtra(LiveCastService.EXTRA_CHANNEL, channel)
            .putExtra(LiveCastService.EXTRA_DEVICE, device)
        // A cast can connect while the app is in the background, where Android won't start one
        try {
            ContextCompat.startForegroundService(context, intent)
            holding = true
        } catch (e: IllegalStateException) {
            Timber.w(e, "Couldn't keep the app alive for the cast")
        }
    }

    override fun release() {
        if (!holding) return
        holding = false
        context.stopService(Intent(context, LiveCastService::class.java))
    }
}
