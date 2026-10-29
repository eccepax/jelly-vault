package org.jellyfin.mobile.livetv.playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
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

/** The channel tuning, playing or failed; null when idle. */
val PlaybackState.channelId: String?
    get() = when (this) {
        is PlaybackState.Tuning -> channelId
        is PlaybackState.Playing -> channelId
        is PlaybackState.Failed -> channelId
        PlaybackState.Idle -> null
    }

/**
 * Live TV playback, built around one rule: every stream the server opens gets closed. The provider
 * allows a single connection, so a leaked stream blocks every channel.
 *
 * - Tuning is serialized: the old stream is closed before the next one opens.
 * - An open in flight is never cancelled (its LiveStreamId would be lost); if the viewer has moved on
 *   by the time it returns, it's closed unplayed.
 * - Zaps stop the current channel at once and tune only where they land, after [ZAP_DEBOUNCE_MS].
 * - Casting: the phone still opens and closes the stream, opened for the cast device while the
 *   player is on one. When the player moves to or from a cast device, the channel is opened again
 *   for where it now plays (closing the old stream first, as ever). In the background the channel
 *   carries on on the cast device; if the cast ends there, the stream is freed as for any background.
 *   Stopped on the cast device itself (its remote, another app casting), the stream is closed and
 *   [onStoppedOnCastDevice] told: the viewer stopped watching, wherever they did it.
 *
 * Not thread-safe: call it, and run [scope], on one thread (the main thread in the app).
 */
class LivePlayback(
    private val streams: LiveStreams,
    private val player: LivePlayer,
    private val closer: StreamCloser,
    private val scope: CoroutineScope,
    private val onStoppedOnCastDevice: () -> Unit = {},
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

    /**
     * A channel freed in the background, tuned again by [resume]; [castEnded] when that was because
     * the cast it played on ended, so a cast coming back (the session rejoined) plays it there again.
     * One freed from here (or failed) waits for the way back: it isn't opened from the background.
     */
    private class Freed(val channelId: String, val castEnded: Boolean)

    private var freed: Freed? = null
    private var inBackground = false
    private var tuneJob: Job? = null
    private var progressJob: Job? = null

    init {
        player.listenWith(
            object : LivePlayer.Listener {
                override fun onError() = onPlayerError()

                override fun onStoppedElsewhere() = onStoppedThere()
            },
        )
        scope.launch { player.onCastDevice.drop(1).collect(::onCastDeviceChanged) }
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

    /**
     * The app went to the background: frees the connection, remembering the channel for [resume].
     * A channel that had failed is tried again on the way back, wherever it failed. A channel on a
     * cast device plays on.
     */
    fun pause() {
        inBackground = true
        if (!player.onCastDevice.value || state.value is PlaybackState.Failed) freeForBackground()
    }

    fun resume() {
        inBackground = false
        val channel = freed?.channelId ?: return
        freed = null
        request(channel, debounce = false)
    }

    /** Stops, returning once the stream is closed: for signing out, while the token still works. */
    suspend fun stopNow() {
        freed = null
        stop()
        tuneJob?.join()
    }

    /** For good (the screen is gone): closes through [closer], since [scope] may be ending too. */
    fun release() {
        generation++
        freed = null
        player.listenWith(null)
        stopPlayer()
        tuneJob?.cancel()
        current?.let { closer.close(it.ticket) }
        current = null
        _state.value = PlaybackState.Idle
    }

    private fun freeForBackground(castEnded: Boolean = false) {
        freed = state.value.channelId?.let { Freed(it, castEnded) }
        if (freed != null) stop()
    }

    /**
     * The player moved to or from a cast device: what's shown is opened again for where it plays now.
     * In the background, a cast that ends frees the stream, and one that comes back (the session
     * rejoined) plays the channel freed there again; not one freed from here.
     */
    private fun onCastDeviceChanged(onCastDevice: Boolean) {
        val freed = freed
        val channel = state.value.channelId
        when {
            onCastDevice && inBackground && channel == null && freed?.castEnded == true -> {
                this.freed = null
                request(freed.channelId, debounce = false)
            }
            channel == null -> Unit
            !onCastDevice && inBackground -> freeForBackground(castEnded = true)
            else -> request(channel, debounce = false)
        }
    }

    private fun onPlayerError() {
        val channel = (state.value as? PlaybackState.Playing)?.channelId ?: return
        Timber.w("Player failed on channel %s", channel)
        stop()
        _state.value = PlaybackState.Failed(channel)
        // Failed on the cast device while in the background: tried again on the way back, as ever
        if (inBackground) freed = Freed(channel, castEnded = false)
    }

    private fun onStoppedThere() {
        if (state.value.channelId == null) return
        Timber.i("The channel was stopped on the cast device")
        freed = null
        stop()
        onStoppedOnCastDevice()
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
        val forCast = player.onCastDevice.value
        val opened = withContext(NonCancellable) { open(channelId, forCast, gen) } ?: return
        player.play(opened.url, channelId, forCast)
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
    private suspend fun open(channelId: String, forCast: Boolean, gen: Int): OpenedStream? {
        val opened = try {
            streams.open(channelId, forCast)
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
