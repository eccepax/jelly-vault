package org.jellyfin.mobile.livetv.playback

/**
 * The channel being watched, as signing out sees it: stopped, and its stream closed, before the
 * rest are (see liveVaultSignOut). Then nothing goes on playing a stream that's closed, and the
 * screen letting go of it later has nothing left to close. App-wide; the screen's playback [hold]s
 * it while it's there.
 */
class WatchingNow {
    private var stop: (suspend () -> Unit)? = null

    /** [stop] stops what's being watched, returning once its stream is closed. */
    fun hold(stop: suspend () -> Unit) {
        this.stop = stop
    }

    /** Lets go, unless another playback holds it now. */
    fun letGo(stop: suspend () -> Unit) {
        if (this.stop === stop) this.stop = null
    }

    suspend fun stop() {
        stop?.invoke()
    }
}
