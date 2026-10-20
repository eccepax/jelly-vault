package org.jellyfin.mobile.player

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.common.playback.SleepTimer
import org.jellyfin.mobile.common.playback.SleepTimerState
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.player.playback.AudiobookPlaybackController
import org.jellyfin.mobile.player.playback.AudiobookPlaybackState
import org.jellyfin.mobile.player.playback.AudiobookQueueLoader
import org.jellyfin.mobile.player.playback.MediaSegmentSkipper
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.sdk.model.api.MediaSegmentDto

/**
 * The player screen's and mini player's view model. Playback itself lives in the playback service
 * (see [AudiobookPlaybackController]), so it carries on with the screens gone.
 */
// One method per control on the player screen.
@Suppress("TooManyFunctions")
class AudiobookPlayerViewModel(
    private val playback: AudiobookPlaybackController,
    private val loader: AudiobookQueueLoader,
    private val sleepTimerEngine: SleepTimer,
    private val segments: MediaSegmentSkipper,
    private val preferences: AudioVaultAppPreferences,
) : ViewModel() {

    val state: StateFlow<AudiobookPlaybackState> = playback.state

    /** Where playback is in the current track; read on demand (it changes continuously). */
    val positionMs: Long get() = playback.positionMs

    val durationMs: Long get() = playback.durationMs

    private val _speed = MutableStateFlow(preferences.playbackSpeed)

    /** The speed chosen, which holding fast-forward doesn't change though the player plays faster. */
    val speed: StateFlow<Float> = _speed.asStateFlow()

    val sleepTimer: StateFlow<SleepTimerState> = sleepTimerEngine.state

    /** The segment playing (an intro, an outro) that the listener is offered to skip; null when none. */
    val skipSegment: StateFlow<MediaSegmentDto?> = segments.offered

    /** Plays what the library or the downloads asked for; says why when it can't. */
    suspend fun start(options: PlayOptions): PlayerException? = loader.load(options).fold(
        onSuccess = { queue ->
            playback.play(queue.tracks, queue.startIndex, queue.startPositionMs)
            null
        },
        onFailure = { it as? PlayerException ?: PlayerException.NetworkFailure(it) },
    )

    fun togglePlayPause() = playback.togglePlayPause()

    fun rewind() = playback.rewind()

    fun fastForward() = playback.fastForward()

    fun previousChapter() = playback.previousChapter()

    fun nextChapter() = playback.nextChapter()

    fun seekTo(positionMs: Long) = playback.seekTo(positionMs)

    fun skipToTrack(index: Int) = playback.skipTo(index)

    fun setSpeed(speed: Float) {
        if (speed == _speed.value) return
        _speed.value = speed
        preferences.playbackSpeed = speed
        playback.setSpeed(speed)
    }

    /** Holding fast-forward plays faster until it's let go, back at the speed chosen. */
    fun holdSpeedUp(holding: Boolean) = playback.setSpeed(
        if (holding) Constants.HOLD_SPEEDUP_MULTIPLIER else _speed.value,
    )

    fun startSleepTimer(minutes: Int) = sleepTimerEngine.start(minutes * MS_PER_MINUTE)

    fun sleepAtEndOfChapter() = sleepTimerEngine.stopAtEndOfSegment()

    fun cancelSleepTimer() = sleepTimerEngine.cancel()

    fun skipOfferedSegment() = segments.skipOffered()

    /** The mini player's stop: nothing left to sleep after either. */
    fun stop() {
        sleepTimerEngine.cancel()
        playback.stop()
    }

    private companion object {
        const val MS_PER_MINUTE = 60_000L
    }
}
