package org.jellyfin.mobile.player.sleeptimer

import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jellyfin.mobile.player.AudiobookPlayerViewModel
import org.jellyfin.mobile.player.ChapterNavigation
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.sdk.model.api.ChapterInfo
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Manages sleep timer functionality for audiobook playback
 * Supports preset durations (15min, 30min, 45min, 60min) and "end of chapter"
 */
class SleepTimerManager(private val viewModel: AudiobookPlayerViewModel) {

    private var timerJob: Job? = null
    private var targetTime: Duration? = null
    private var timerType: SleepTimerType? = null
    private var timerStartMark: TimeMark? = null

    /**
     * Sleep timer duration presets
     */
    enum class SleepTimerDuration(val duration: Duration) {
        FIFTEEN_MINUTES(15.minutes),
        THIRTY_MINUTES(30.minutes),
        FORTY_FIVE_MINUTES(45.minutes),
        SIXTY_MINUTES(60.minutes),
    }

    /**
     * Type of sleep timer
     */
    enum class SleepTimerType {
        DURATION, // Fixed duration (15min, 30min, etc.)
        END_OF_CHAPTER, // Stop at end of current chapter
    }

    /**
     * Start sleep timer with specified duration
     */
    fun startTimer(duration: SleepTimerDuration) {
        cancelTimer()
        timerType = SleepTimerType.DURATION
        targetTime = duration.duration
        timerStartMark = TimeSource.Monotonic.markNow()

        timerJob = viewModel.viewModelScope.launch {
            Timber.d("Sleep timer started: ${duration.duration}")
            delay(duration.duration)
            if (isActive) {
                Timber.d("Sleep timer expired, pausing playback")
                viewModel.pause()
                cancelTimer()
            }
        }
    }

    /**
     * Start sleep timer to stop at end of current chapter
     */
    fun startTimerEndOfChapter() {
        cancelTimer()
        timerType = SleepTimerType.END_OF_CHAPTER

        val duration = resolveEndOfChapterDuration() ?: return
        targetTime = duration
        startDurationTimer(duration)
    }

    private fun resolveEndOfChapterDuration(): Duration? {
        val player = viewModel.playerOrNull
        val mediaSource = viewModel.mediaSourceOrNull
        val chapters = mediaSource?.item?.chapters

        if (player == null || mediaSource == null || chapters == null) {
            val missing = describeMissingEndOfChapterPrerequisite(player, mediaSource, chapters)
            Timber.w("Cannot start end-of-chapter timer: $missing")
            return null
        }

        // Chapters are positions in the whole book, which a transcode's own position isn't
        val currentPosition = viewModel.adjustedCurrentPosition.milliseconds
        val nextChapterStart = ChapterNavigation.nextChapterStartPosition(chapters, currentPosition)

        // No next chapter — fall back to end of media
        return if (nextChapterStart == null || nextChapterStart == Duration.INFINITE) {
            resolveEndOfMediaDuration(mediaSource, currentPosition)
        } else {
            resolveTimeUntilNextChapter(nextChapterStart, currentPosition)
        }
    }

    private fun describeMissingEndOfChapterPrerequisite(
        player: ExoPlayer?,
        mediaSource: JellyfinMediaSource?,
        chapters: List<ChapterInfo>?,
    ): String = when {
        player == null -> "player not available"
        mediaSource == null -> "media source not available"
        chapters == null -> "no chapters available"
        else -> "unknown"
    }

    private fun resolveEndOfMediaDuration(mediaSource: JellyfinMediaSource, currentPosition: Duration): Duration? {
        if (mediaSource.runTime <= Duration.ZERO) {
            Timber.w("Cannot start end-of-chapter timer: media duration not available yet")
            return null
        }
        val duration = mediaSource.runTime - currentPosition
        if (duration <= Duration.ZERO) {
            Timber.w("Cannot start end-of-chapter timer: already at end")
            return null
        }
        return duration
    }

    private fun resolveTimeUntilNextChapter(nextChapterStart: Duration, currentPosition: Duration): Duration? {
        val timeUntilNextChapter = nextChapterStart - currentPosition
        if (timeUntilNextChapter <= Duration.ZERO) {
            Timber.w("Cannot start end-of-chapter timer: already past chapter start")
            return null
        }
        return timeUntilNextChapter
    }

    /**
     * Start a duration-based timer (internal helper)
     */
    private fun startDurationTimer(duration: Duration) {
        timerStartMark = TimeSource.Monotonic.markNow()
        timerJob = viewModel.viewModelScope.launch {
            Timber.d("Sleep timer started: $duration")
            delay(duration)
            if (isActive) {
                Timber.d("Sleep timer expired, pausing playback")
                viewModel.pause()
                cancelTimer()
            }
        }
    }

    /**
     * Cancel the current sleep timer
     */
    fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        targetTime = null
        timerType = null
        timerStartMark = null
        Timber.d("Sleep timer cancelled")
    }

    /**
     * Check if timer is active
     */
    fun isActive(): Boolean = timerJob?.isActive == true

    /**
     * Get remaining time on timer, or null if not active
     */
    fun getRemainingTime(): Duration? {
        val target = targetTime ?: return null
        val job = timerJob ?: return null
        val startMark = timerStartMark ?: return null
        if (!job.isActive) return null

        val elapsed = startMark.elapsedNow()
        val remaining = target - elapsed
        return remaining.coerceAtLeast(Duration.ZERO)
    }

    /**
     * Get the type of active timer
     */
    fun getTimerType(): SleepTimerType? = timerType
}
