package org.jellyfin.mobile.music.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SleepTimerState {
    data object Off : SleepTimerState
    data class Counting(val remainingMs: Long) : SleepTimerState

    /** Pauses when the playing song finishes. */
    data object EndOfSong : SleepTimerState
}

/**
 * Pauses playback after a while, or at the end of the playing song. A countdown fades the music
 * out over its last [FADE_MS] rather than cutting it off, and puts the volume back afterwards.
 * One per app: it keeps running whatever screen is showing. Must run on the main thread, like
 * [playback].
 */
class SleepTimer(
    private val playback: PlaybackController,
    private val scope: CoroutineScope,
    /** Shared with volume levelling, so the fade starts from the levelled volume. */
    private val volume: PlaybackVolume = PlaybackVolume(playback),
) {
    private val _state = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private var job: Job? = null
    private var faded = false

    fun start(durationMs: Long) {
        replaceJob(SleepTimerState.Counting(durationMs)) { countDown(durationMs) }
    }

    /** Adds [byMs] to a running countdown. */
    fun extend(byMs: Long = EXTEND_MS) {
        val counting = _state.value as? SleepTimerState.Counting ?: return
        start(counting.remainingMs + byMs)
    }

    fun stopAtEndOfSong() {
        replaceJob(SleepTimerState.EndOfSong) { watchForSongEnd() }
    }

    fun cancel() {
        job?.cancel()
        job = null
        restoreVolume()
        _state.value = SleepTimerState.Off
    }

    private fun replaceJob(state: SleepTimerState, work: suspend () -> Unit) {
        job?.cancel()
        restoreVolume()
        _state.value = state
        job = scope.launch { work() }
    }

    private suspend fun countDown(durationMs: Long) {
        var remainingMs = durationMs
        while (remainingMs > 0) {
            delay(TICK_MS)
            remainingMs = (remainingMs - TICK_MS).coerceAtLeast(0)
            _state.value = SleepTimerState.Counting(remainingMs)
            if (remainingMs in 1 until FADE_MS) {
                faded = true
                volume.setFade(remainingMs.toFloat() / FADE_MS)
            }
        }
        playback.pause()
        restoreVolume()
        _state.value = SleepTimerState.Off
    }

    /**
     * Watches for the playing song to finish by itself. Skipping to another song isn't finishing
     * it, so the timer waits for that one instead; a song is taken to have finished when the
     * player moved on from within its last moments.
     */
    private suspend fun watchForSongEnd() {
        var last = Sample.now(playback)
        while (true) {
            delay(TICK_MS)
            val now = Sample.now(playback)
            if (now.entryUid != last.entryUid) {
                if (last.isNearEnd) {
                    playback.pause()
                    playback.seekTo(0L)
                    break
                }
            } else if (!now.isPlaying && now.isNearEnd) {
                break // The last song in the queue ended.
            }
            last = now
        }
        _state.value = SleepTimerState.Off
    }

    private fun restoreVolume() {
        if (!faded) return
        faded = false
        volume.setFade(1f)
    }

    private data class Sample(val entryUid: Long?, val positionMs: Long, val durationMs: Long, val isPlaying: Boolean) {
        val isNearEnd: Boolean get() = durationMs > 0 && positionMs >= durationMs - END_WINDOW_MS

        companion object {
            fun now(playback: PlaybackController): Sample {
                val state = playback.state.value
                val entry = state.queue.entries.getOrNull(state.queue.currentIndex)
                val durationMs = entry?.track?.durationMs?.takeIf { it > 0 } ?: playback.durationMs
                return Sample(entry?.uid, playback.positionMs, durationMs, state.isPlaying)
            }
        }
    }

    companion object {
        const val TICK_MS = 500L
        const val FADE_MS = 10_000L
        const val EXTEND_MS = 15 * 60_000L

        /** A sample this close to a song's end means it played out (samples are [TICK_MS] apart). */
        private const val END_WINDOW_MS = 2_500L
    }
}
