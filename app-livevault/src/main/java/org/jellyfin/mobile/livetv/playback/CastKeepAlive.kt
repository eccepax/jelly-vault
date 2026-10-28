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

/**
 * [CastKeepAlive] through [LiveCastService], a foreground service with a notification. What's held
 * is [CastHold]'s, which the service follows: a running service shows another channel in its
 * notification, and stops itself once nothing is held. It's never stopped from here, which could
 * be before it has gone to the foreground, as Android requires of a service started for it.
 */
class ServiceCastKeepAlive(private val context: Context) : CastKeepAlive {

    override fun hold(channel: String, device: String?) {
        CastHold.current = channel to device
        if (LiveCastService.running) return
        // A cast can connect while the app is in the background, where Android won't start one:
        // it's asked again from the foreground (see WatchViewModel.onForeground)
        try {
            ContextCompat.startForegroundService(context, Intent(context, LiveCastService::class.java))
        } catch (e: IllegalStateException) {
            Timber.w(e, "Couldn't keep the app alive for the cast")
        }
    }

    override fun release() {
        CastHold.current = null
    }
}

/** What the app is kept alive for: the channel and the cast device it plays on; null while nothing is. Main thread only. */
internal object CastHold {
    var current: Pair<String, String?>? = null
        set(value) {
            field = value
            listener?.invoke(value)
        }

    /** The running [LiveCastService]. */
    var listener: ((Pair<String, String?>?) -> Unit)? = null
}
