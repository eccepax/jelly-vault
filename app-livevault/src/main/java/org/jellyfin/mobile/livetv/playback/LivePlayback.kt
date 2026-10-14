package org.jellyfin.mobile.livetv.playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

sealed interface PlaybackState {
    data object Idle : PlaybackState

    /** The server is opening the channel (seconds), or a zap is about to land on it. */
    data class Tuning(val channelId: String) : PlaybackState
    data class Playing(val channelId: String) : PlaybackState
    data class Failed(val channelId: String) : PlaybackState
}

/**
 * Live TV playback, built around one rule: every stream the server opens gets closed. The provider
 * allows a single connection, so a leaked stream blocks every channel.
 *
 * - Tuning is serialized: the old stream is closed before the next one opens.
 * - An open in flight is never cancelled (its LiveStreamId would be lost); if the viewer has moved on
 *   by the time it returns, it's closed unplayed.
 * - Zaps stop the current channel at once and tune only where they land, after [ZAP_DEBOUNCE_MS].
 *
 * Not thread-safe: call it, and run [scope], on one thread (the main thread in the app).
 */
class LivePlayback(
    private val streams: LiveStreams,
    private val player: LivePlayer,
    private val closer: StreamCloser,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val tuning = Mutex()

    /** Bumped by every request, so a tune can tell it has been superseded. */
    private var generation = 0
    private var current: OpenedStream? = null
    private var lineup: List<String> = emptyList()

    /** The channel the viewer last asked for: where a zap starts from, and what retry tunes. */
    private var lastChannel: String? = null
    private var resumeChannel: String? = null
    private var tuneJob: Job? = null
    private var progressJob: Job? = null

    init {
        player.listenWith { onPlayerError() }
    }

    /** Tunes [channelId]; [lineup] is the channel order zapping moves through. */
    fun play(channelId: String, lineup: List<String>) {
        this.lineup = lineup
        request(channelId, debounce = false)
    }

    /** Moves [step] channels through the lineup (wrapping), tuning once the zapping stops. */
    fun zap(step: Int) {
        val index = lineup.indexOf(lastChannel ?: return)
        if (index < 0) return
        request(lineup[(index + step).mod(lineup.size)], debounce = true)
    }

    fun retry() {
        lastChannel?.let { request(it, debounce = false) }
    }

    fun stop() {
        request(null, debounce = false)
    }

    /** The app went to the background: frees the connection, remembering the channel for [resume]. */
    fun pause() {
        resumeChannel = when (val state = state.value) {
            is PlaybackState.Playing -> state.channelId
            is PlaybackState.Tuning -> state.channelId
            else -> null
        }
        if (resumeChannel != null) stop()
    }

    fun resume() {
        val channel = resumeChannel ?: return
        resumeChannel = null
        request(channel, debounce = false)
    }

    /** For good (the screen is gone): closes through [closer], since [scope] may be ending too. */
    fun release() {
        generation++
        resumeChannel = null
        player.listenWith(null)
        stopPlayer()
        tuneJob?.cancel()
        current?.let { closer.close(it.ticket) }
        current = null
        _state.value = PlaybackState.Idle
    }

    private fun onPlayerError() {
        val channel = (state.value as? PlaybackState.Playing)?.channelId ?: return
        Timber.w("Player failed on channel %s", channel)
        stop()
        _state.value = PlaybackState.Failed(channel)
    }

    private fun request(channelId: String?, debounce: Boolean) {
        val gen = ++generation
        if (channelId != null) lastChannel = channelId
        _state.value = if (channelId != null) PlaybackState.Tuning(channelId) else PlaybackState.Idle
        stopPlayer()
        tuneJob?.cancel()
        tuneJob = scope.launch {
            tuning.withLock {
                closeCurrent()
                if (channelId == null) return@withLock
                if (debounce) delay(ZAP_DEBOUNCE_MS)
                tune(channelId, gen)
            }
        }
    }

    private suspend fun tune(channelId: String, gen: Int) {
        val opened = withContext(NonCancellable) { open(channelId, gen) } ?: return
        player.play(opened.url)
        _state.value = PlaybackState.Playing(channelId)
        report(opened)
        progressJob = scope.launch {
            while (true) {
                delay(PROGRESS_INTERVAL_MS)
                report(opened)
            }
        }
    }

    /** Opens [channelId] and records it; null when it failed or [gen] was superseded meanwhile (then it's closed). */
    private suspend fun open(channelId: String, gen: Int): OpenedStream? {
        val opened = try {
            streams.open(channelId)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Timber.e(e, "Couldn't open channel %s", channelId)
            if (gen == generation) _state.value = PlaybackState.Failed(channelId)
            return null
        }
        closer.track(opened.ticket)
        if (gen != generation) {
            // The viewer moved on while the server was tuning.
            closer.closeNow(opened.ticket)
            return null
        }
        current = opened
        return opened
    }

    private suspend fun closeCurrent() {
        val stream = current ?: return
        current = null
        closer.closeNow(stream.ticket)
    }

    private fun stopPlayer() {
        progressJob?.cancel()
        progressJob = null
        player.stop()
    }

    private suspend fun report(stream: OpenedStream) {
        try {
            streams.reportPlaying(stream)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Timber.w(e, "Reporting live playback failed")
        }
    }

    companion object {
        const val PROGRESS_INTERVAL_MS = 10_000L
        const val ZAP_DEBOUNCE_MS = 800L
    }
}
