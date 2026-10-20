package org.jellyfin.mobile.common.playback

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

    /** Pauses when the playing segment (a song, a chapter) finishes. */
    data object EndOfSegment : SleepTimerState
}

/**
 * Where playback is: which segment is playing (a song, an audiobook chapter) and how far into it.
 * [positionMs] and [durationMs] are within the segment; [durationMs] is 0 when not known yet.
 */
data class PlaybackSample(
    val segmentId: Any?,
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
)

/** The player as the sleep timer sees it. */
interface SleepTimerTarget {
    fun sample(): PlaybackSample
    fun pause()

    /** Back to where the playing segment began: the timer stops just after the next one has started. */
    fun rewindToSegmentStart()

    /** Scales the volume, 0 to 1, for fading out; 1 puts it back. */
    fun setFade(fade: Float)
}

/**
 * Pauses playback after a while, or at the end of the playing segment (a song, a chapter). A
 * countdown fades out over its last [FADE_MS] rather than cutting off, and puts the volume back
 * afterwards. One per app: it keeps running whatever screen is showing. Must run on the main
 * thread, like the player behind [target].
 */
class SleepTimer(
    private val target: SleepTimerTarget,
    private val scope: CoroutineScope,
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

    fun stopAtEndOfSegment() {
        replaceJob(SleepTimerState.EndOfSegment) { watchForSegmentEnd() }
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
                target.setFade(remainingMs.toFloat() / FADE_MS)
            }
        }
        target.pause()
        restoreVolume()
        _state.value = SleepTimerState.Off
    }

    /**
     * Watches for the playing segment to finish by itself. Skipping to another segment isn't
     * finishing it, so the timer waits for that one instead; a segment is taken to have finished
     * when playback moved on from within its last moments. It follows the playback position rather
     * than the clock, so pauses and playback speed don't throw it off.
     */
    private suspend fun watchForSegmentEnd() {
        var last = target.sample()
        while (true) {
            delay(TICK_MS)
            val now = target.sample()
            if (now.segmentId != last.segmentId) {
                if (last.isNearEnd) {
                    target.pause()
                    target.rewindToSegmentStart()
                    break
                }
            } else if (!now.isPlaying && now.isNearEnd) {
                break // The last segment ended.
            }
            last = now
        }
        _state.value = SleepTimerState.Off
    }

    private fun restoreVolume() {
        if (!faded) return
        faded = false
        target.setFade(1f)
    }

    private val PlaybackSample.isNearEnd: Boolean
        get() = durationMs > 0 && positionMs >= durationMs - END_WINDOW_MS

    companion object {
        const val TICK_MS = 500L
        const val FADE_MS = 10_000L
        const val EXTEND_MS = 15 * 60_000L

        /** A sample this close to a segment's end means it played out (samples are [TICK_MS] apart). */
        private const val END_WINDOW_MS = 2_500L
    }
}
