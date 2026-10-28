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
 * stops or plays to its end. Streamed tracks report to [server], downloaded ones to [device], unless
 * they play on a cast device, which streams them from the server. A listen reports to one of them
 * throughout: a download moving onto a cast device or back ends its listen and starts another.
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
    private class Listen(
        val index: Int,
        val track: AudiobookTrack,
        var lastPositionMs: Long,
        val sink: AudiobookProgressSink,
    ) {
        var sinceProgressMs = 0L
        var playWhenReady = false

        /** Ended (played out, or failed) and reported as such: nothing more until it starts again. */
        var over = false

        /** Over because it played to the end: still at the end (back from a cast device, say) isn't starting again. */
        var finished = false
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
            else -> onSameTrack(current, track, state)
        }
    }

    /**
     * What changed of the track [current] listens to. An end or an error is the listen's where it
     * played, before any move to where it plays from now (they can come in the same change).
     */
    private fun onSameTrack(current: Listen, track: AudiobookTrack, state: AudiobookPlaybackState) {
        when {
            current.over -> if (startsAgain(current, track, state)) listen = begin(state.currentIndex, track, state)
            state.isEnded -> {
                current.over = true
                current.finished = true
                current.report { finished(track) }
            }
            state.error != null -> {
                current.over = true
                current.report { stopped(track, current.lastPositionMs) }
            }
            current.sink !== sinkFor(track, state) -> {
                // Where it plays from changed: the listen ends where it got to, and goes on from there
                current.lastPositionMs = controller.positionMs
                end(current)
                listen = begin(state.currentIndex, track, state)
            }
            state.playWhenReady != current.playWhenReady -> {
                current.playWhenReady = state.playWhenReady
                current.lastPositionMs = controller.positionMs
                val positionMs = current.lastPositionMs
                current.report { progress(track, positionMs, isPaused = !state.playWhenReady) }
            }
        }
    }

    /** An ended listen's track plays again: not ended or failed, and, if it finished, moved off its end. */
    private fun startsAgain(over: Listen, track: AudiobookTrack, state: AudiobookPlaybackState): Boolean =
        !state.isEnded && state.error == null && !(over.finished && isAtEnd(track, controller.positionMs))

    private fun isAtEnd(track: AudiobookTrack, positionMs: Long) =
        track.durationMs > 0 && positionMs >= track.durationMs - END_WINDOW_MS

    private fun begin(index: Int, track: AudiobookTrack, state: AudiobookPlaybackState): Listen {
        val positionMs = controller.positionMs
        return Listen(index, track, positionMs, sinkFor(track, state)).apply {
            playWhenReady = state.playWhenReady
            report { started(track, positionMs) }
        }
    }

    /** A download playing on a cast device is streamed there, from the server. */
    private fun sinkFor(track: AudiobookTrack, state: AudiobookPlaybackState) =
        if (track.fromDevice && !state.isRemote) device else server

    /** The listen moved on from [ended]: finished if it got to the track's last moments. */
    private fun end(ended: Listen) {
        if (ended.over) return
        val track = ended.track
        val positionMs = ended.lastPositionMs
        val finished = isAtEnd(track, positionMs)
        ended.report { if (finished) finished(track) else stopped(track, positionMs) }
    }

    private fun onSample() {
        val current = listen ?: return
        if (current.over) {
            // A seek off a finished track's end changes nothing else, so it's only seen here
            val state = controller.state.value
            val track = state.currentTrack
            if (track != null && track.id == current.track.id && startsAgain(current, track, state)) {
                listen = begin(state.currentIndex, track, state)
            }
            return
        }
        current.lastPositionMs = controller.positionMs
        if (!controller.state.value.isPlaying) return
        current.sinceProgressMs += SAMPLE_INTERVAL_MS
        if (current.sinceProgressMs >= PROGRESS_INTERVAL_MS) {
            current.sinceProgressMs = 0L
            val positionMs = current.lastPositionMs
            current.report { progress(current.track, positionMs, isPaused = false) }
        }
    }

    private fun Listen.report(send: suspend AudiobookProgressSink.() -> Unit) {
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
