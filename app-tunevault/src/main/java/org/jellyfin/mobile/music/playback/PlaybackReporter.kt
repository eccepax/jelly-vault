package org.jellyfin.mobile.music.playback

import java.time.Instant

/** Tells the server what's being played, so play counts, history and resume points stay accurate. */
interface PlaybackReporter {
    suspend fun started(trackId: String, positionMs: Long, isPaused: Boolean)
    suspend fun progress(trackId: String, positionMs: Long, isPaused: Boolean)

    /** The track stopped being the current one; [positionMs] is where it got to. */
    suspend fun stopped(trackId: String, positionMs: Long)

    /** Records a play that happened at [at] while the server couldn't be told (see [OfflinePlayReporter]). */
    suspend fun playedAt(trackId: String, at: Instant)
}
