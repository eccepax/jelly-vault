package org.jellyfin.mobile.music.playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.mobile.music.domain.QueueEntry
import timber.log.Timber

/**
 * Watches playback and reports it to the server, counting a track only once it's genuinely been
 * listened to: half of it or [MAX_THRESHOLD_MS], whichever comes first (Last.fm's scrobble rule).
 * Jellyfin counts any reported music play as a full play, so skips are never reported at all.
 * Listening is measured in time actually playing, so pauses and forward seeks don't count.
 *
 * Reports go through a queue and are sent one at a time; a failed one is logged and dropped.
 * Must run on the same thread as [playback] (Main in the app).
 */
class PlaybackReportingTracker(
    private val playback: PlaybackController,
    private val reporter: PlaybackReporter,
    private val scope: CoroutineScope,
) {
    private class Session(val entry: QueueEntry, var lastPositionMs: Long) {
        var listenedMs = 0L
        var reported = false
        var sinceProgressMs = 0L

        val thresholdMs: Long
            get() = entry.track.durationMs.takeIf { it > 0 }?.let { minOf(it / 2, MAX_THRESHOLD_MS) } ?: MAX_THRESHOLD_MS
    }

    private val reports = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var session: Session? = null
    private var isPlaying = false

    fun start(): Job = scope.launch {
        launch { sendReports() }
        launch { playback.state.collect(::onState) }
        launch {
            while (true) {
                delay(SAMPLE_INTERVAL_MS)
                onSample()
            }
        }
    }

    /**
     * Ends the current track's session now and waits (up to [FINISH_TIMEOUT_MS]) until every report
     * so far has gone, for sign-out: once the token is gone, nothing more can be reported.
     */
    suspend fun finish() {
        endSession()
        session = null
        val sent = CompletableDeferred<Unit>()
        report { sent.complete(Unit) }
        withTimeoutOrNull(FINISH_TIMEOUT_MS) { sent.await() }
    }

    private fun onState(state: PlaybackState) {
        val entry = state.queue.entries.getOrNull(state.queue.currentIndex)
        val wasPlaying = isPlaying
        isPlaying = state.isPlaying
        val current = session
        if (entry?.uid != current?.entry?.uid) {
            endSession()
            session = entry?.let { Session(it, playback.positionMs) }
        } else if (current != null && current.reported && wasPlaying != isPlaying) {
            report { reporter.progress(current.entry.track.id, playback.positionMs, isPaused = !isPlaying) }
        }
    }

    private fun onSample() {
        var current = session ?: return
        val positionMs = playback.positionMs
        if (isPlaying && isReplay(current, positionMs)) {
            endSession()
            current = Session(current.entry, positionMs).also { session = it }
        }
        current.lastPositionMs = positionMs
        if (!isPlaying) return

        current.listenedMs += SAMPLE_INTERVAL_MS
        val trackId = current.entry.track.id
        if (!current.reported) {
            if (current.listenedMs >= current.thresholdMs) {
                current.reported = true
                current.sinceProgressMs = 0L
                report { reporter.started(trackId, positionMs, isPaused = false) }
            }
        } else {
            current.sinceProgressMs += SAMPLE_INTERVAL_MS
            if (current.sinceProgressMs >= PROGRESS_INTERVAL_MS) {
                current.sinceProgressMs = 0L
                report { reporter.progress(trackId, positionMs, isPaused = false) }
            }
        }
    }

    /** The same entry jumped from its last seconds back to the start: repeat-one looped it. */
    private fun isReplay(session: Session, positionMs: Long): Boolean {
        val durationMs = session.entry.track.durationMs
        return durationMs > 0 &&
            positionMs < session.lastPositionMs &&
            session.lastPositionMs >= durationMs - REPLAY_END_WINDOW_MS
    }

    private fun endSession() {
        val ended = session?.takeIf { it.reported } ?: return
        val trackId = ended.entry.track.id
        val positionMs = ended.lastPositionMs
        report { reporter.stopped(trackId, positionMs) }
    }

    private fun report(send: suspend () -> Unit) {
        reports.trySend(send)
    }

    private suspend fun sendReports() {
        for (send in reports) {
            try {
                send()
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.w(e, "Couldn't report playback to the server")
            }
        }
    }

    companion object {
        const val SAMPLE_INTERVAL_MS = 1_000L
        const val PROGRESS_INTERVAL_MS = 10_000L
        const val MAX_THRESHOLD_MS = 240_000L
        const val FINISH_TIMEOUT_MS = 3_000L
        private const val REPLAY_END_WINDOW_MS = 5_000L
    }
}
