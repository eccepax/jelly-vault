package org.jellyfin.mobile.music.playback

import kotlinx.coroutines.CancellationException
import org.jellyfin.mobile.common.server.worthRetrying
import timber.log.Timber
import java.time.Instant

/**
 * Reports to the server, keeping any play it can't hear (no network, or a server not ready for it:
 * see [worthRetrying]) to [sync] later with the time it happened. A play is counted at "started"
 * (see [PlaybackReportingTracker]), so only those are kept; progress and stops that can't be sent
 * are let go. A play the server refuses as such isn't kept either: resending wouldn't help.
 */
class OfflinePlayReporter(
    private val server: PlaybackReporter,
    private val pending: PendingPlayDao,
    private val now: () -> Instant = Instant::now,
) : PlaybackReporter {

    override suspend fun started(trackId: String, positionMs: Long, isPaused: Boolean) {
        val heardAt = now()
        send { server.started(trackId, positionMs, isPaused) } ?: return
        pending.insert(PendingPlayEntity(trackId = trackId, playedAtMs = heardAt.toEpochMilli()))
    }

    override suspend fun progress(trackId: String, positionMs: Long, isPaused: Boolean) {
        send { server.progress(trackId, positionMs, isPaused) }
    }

    override suspend fun stopped(trackId: String, positionMs: Long) {
        send { server.stopped(trackId, positionMs) }
    }

    override suspend fun playedAt(trackId: String, at: Instant) = server.playedAt(trackId, at)

    /** Sends the kept plays, oldest first, until the server can't take them. */
    suspend fun sync() {
        for (play in pending.all()) {
            send { server.playedAt(play.trackId, Instant.ofEpochMilli(play.playedAtMs)) }?.let { return }
            pending.delete(play.id)
        }
    }

    suspend fun clear() = pending.deleteAll()

    /** Runs [report]; returns the failure if it's worth sending again later, else null. */
    private suspend fun send(report: suspend () -> Unit): Exception? = try {
        report()
        null
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        if (e.worthRetrying()) {
            e
        } else {
            Timber.w(e, "The server refused a playback report")
            null
        }
    }
}
