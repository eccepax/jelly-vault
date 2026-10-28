package org.jellyfin.mobile.player.playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/** Where listening is told about: the server for streamed tracks, the device for downloaded ones. */
interface AudiobookProgressSink {
    suspend fun started(track: AudiobookTrack, positionMs: Long)
    suspend fun progress(track: AudiobookTrack, positionMs: Long, isPaused: Boolean)

    /** The track stopped being listened to part way, at [positionMs]. */
    suspend fun stopped(track: AudiobookTrack, positionMs: Long)

    /** The track was listened to the end: next time it starts over. */
    suspend fun finished(track: AudiobookTrack)
}

/**
 * Follows playback and reports where the listener is, the resume position the whole app depends
 * on: when a track starts, every [PROGRESS_INTERVAL_MS] while it plays, on each pause, and when it
 * stops or plays to its end. Streamed tracks report to [server], downloaded ones to [device].
 * App-wide, like the player in the service, so it keeps reporting with the app's screens gone.
 *
 * Reports go through a queue and are sent one at a time; a failed one is logged and dropped.
 * Must run on the same thread as [controller] (Main in the app).
 */
class AudiobookProgressReporter(
    private val controller: AudiobookPlaybackController,
    private val server: AudiobookProgressSink,
    private val device: AudiobookProgressSink,
    private val scope: CoroutineScope,
) {
    private class Listen(val index: Int, val track: AudiobookTrack, var lastPositionMs: Long) {
        var sinceProgressMs = 0L
        var playWhenReady = false

        /** Ended (played out, or failed) and reported as such: nothing more until it starts again. */
        var over = false
    }

    private val reports = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var listen: Listen? = null

    /** The track (its index and id) signed out of, still playing until its stop comes through. */
    private var signedOutOf: Pair<Int, String>? = null

    fun start(): Job = scope.launch {
        launch { sendReports() }
        launch { controller.state.collect(::onState) }
        launch {
            while (true) {
                delay(SAMPLE_INTERVAL_MS)
                onSample()
            }
        }
    }

    /**
     * For signing out: ends the listen, stops the book, and waits (up to [FINISH_TIMEOUT_MS]) until
     * every report so far has gone, as once the token is gone nothing more can be reported. The
     * listen ends before the book stops: through the app's MediaController the position is gone the
     * moment it stops, before its state says so.
     */
    suspend fun finishAndStop() {
        endListen()
        // Until the stop comes through, the book playing on isn't a new listen to report
        val state = controller.state.value
        signedOutOf = state.currentTrack?.let { state.currentIndex to it.id }
        controller.stop()
        awaitReports()
    }

    private fun endListen() {
        listen?.let { ended ->
            ended.lastPositionMs = controller.positionMs.takeIf { controller.state.value.currentTrack != null }
                ?: ended.lastPositionMs
            end(ended)
        }
        listen = null
    }

    private suspend fun awaitReports() {
        val sent = CompletableDeferred<Unit>()
        reports.trySend { sent.complete(Unit) }
        withTimeoutOrNull(FINISH_TIMEOUT_MS) { sent.await() }
    }

    private fun onState(state: AudiobookPlaybackState) {
        signedOutOf?.let { (index, id) ->
            if (state.currentIndex == index && state.currentTrack?.id == id) return
            signedOutOf = null
        }
        val current = listen
        val track = state.currentTrack
        when {
            track == null || current == null || current.index != state.currentIndex || current.track.id != track.id -> {
                current?.let(::end)
                listen = track?.let { begin(state.currentIndex, it, state) }
            }
            current.over -> if (!state.isEnded && state.error == null) listen = begin(state.currentIndex, track, state)
            state.isEnded -> {
                current.over = true
                report(track) { finished(track) }
            }
            state.error != null -> {
                current.over = true
                report(track) { stopped(track, current.lastPositionMs) }
            }
            state.playWhenReady != current.playWhenReady -> {
                current.playWhenReady = state.playWhenReady
                current.lastPositionMs = controller.positionMs
                val positionMs = current.lastPositionMs
                report(track) { progress(track, positionMs, isPaused = !state.playWhenReady) }
            }
        }
    }

    private fun begin(index: Int, track: AudiobookTrack, state: AudiobookPlaybackState): Listen {
        val positionMs = controller.positionMs
        report(track) { started(track, positionMs) }
        return Listen(index, track, positionMs).apply { playWhenReady = state.playWhenReady }
    }

    /** The listen moved on from [ended]: finished if it got to the track's last moments. */
    private fun end(ended: Listen) {
        if (ended.over) return
        val track = ended.track
        val positionMs = ended.lastPositionMs
        val finished = track.durationMs > 0 && positionMs >= track.durationMs - END_WINDOW_MS
        report(track) { if (finished) finished(track) else stopped(track, positionMs) }
    }

    private fun onSample() {
        val current = listen ?: return
        if (current.over) return
        current.lastPositionMs = controller.positionMs
        if (!controller.state.value.isPlaying) return
        current.sinceProgressMs += SAMPLE_INTERVAL_MS
        if (current.sinceProgressMs >= PROGRESS_INTERVAL_MS) {
            current.sinceProgressMs = 0L
            val positionMs = current.lastPositionMs
            report(current.track) { progress(current.track, positionMs, isPaused = false) }
        }
    }

    private fun report(track: AudiobookTrack, send: suspend AudiobookProgressSink.() -> Unit) {
        // A download playing on a cast device is streamed there, from the server
        val sink = if (track.fromDevice && !controller.state.value.isRemote) device else server
        reports.trySend { sink.send() }
    }

    private suspend fun sendReports() {
        for (send in reports) {
            try {
                send()
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.w(e, "Couldn't report listening")
            }
        }
    }

    companion object {
        const val SAMPLE_INTERVAL_MS = 1_000L
        const val PROGRESS_INTERVAL_MS = 10_000L
        const val FINISH_TIMEOUT_MS = 3_000L

        /** Moving on from this close to a track's end counts as having finished it. */
        private const val END_WINDOW_MS = 5_000L
    }
}
