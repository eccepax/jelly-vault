package org.jellyfin.mobile.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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

    private val _startFailures = Channel<PlayerException>(Channel.BUFFERED)

    /**
     * Why the book last asked for couldn't be started; kept until it's heard, so a player screen
     * not showing yet (the app in the background) still hears it. Dropped once another book is
     * asked for, or the player screen is left (see [forgetStartFailures]): it'd be out of place then.
     */
    val startFailures: Flow<PlayerException> = _startFailures.receiveAsFlow()

    /** Counts the books asked for, so a start that's been overtaken keeps its failure to itself. */
    private var request = 0

    /** The book asked for and not yet playing; null when there's none. */
    private val _starting = MutableStateFlow<Starting?>(null)

    /**
     * Whether there's a book to show, and whether one asked for isn't playing yet: worked out from
     * one look at both, so the book arriving and its starting being over are seen together.
     * A book is starting until it's the one playing, or for [START_TIMEOUT_MS] at most (the service
     * never coming up, or stopped first); one that fails stays starting until another is asked for,
     * as the player screen leaves on the failure itself. The screen, showing no book and none
     * starting (as it's restored with the app's process gone), has nothing to show.
     */
    val presence: StateFlow<PlayerPresence> = combine(playback.state, _starting, ::presenceOf)
        .stateIn(viewModelScope, SharingStarted.Eagerly, presenceOf(playback.state.value, null))

    /** Where playback is, about 4 times a second while followed: the player has no event for it moving. */
    val progress: Flow<PlayerProgress> = flow {
        while (true) {
            emit(PlayerProgress(positionMs = playback.positionMs, durationMs = playback.durationMs))
            delay(PROGRESS_INTERVAL_MS)
        }
    }

    /** Whether holding fast-forward speeds playback up: the setting, as it is now. */
    val holdToSpeedUp: Boolean get() = preferences.exoPlayerAllowPressSpeedUp

    /**
     * Plays what the library or the downloads asked for, in this view model's scope so a rotation
     * doesn't cut it short. Why it can't goes to [startFailures].
     */
    fun play(options: PlayOptions) {
        val asked = nextRequest()
        _starting.value = Starting.Loading
        viewModelScope.launch {
            val started = start(options)
            if (asked != request) return@launch
            started.fold(
                onSuccess = { trackId ->
                    _starting.value = Starting.Playing(trackId)
                    // The service tells of it playing a moment after it's asked, perhaps after
                    // still telling of the book that was playing
                    withTimeoutOrNull(START_TIMEOUT_MS) { playback.state.first { it.currentTrack?.id == trackId } }
                    if (asked == request) _starting.value = null
                },
                onFailure = { _startFailures.send(it as PlayerException) },
            )
        }
    }

    /** The player screen's been left: why a book couldn't start is no longer for anyone to hear. */
    fun forgetStartFailures() {
        nextRequest()
        _starting.value = null
    }

    private fun nextRequest(): Int {
        while (_startFailures.tryReceive().isSuccess) Unit
        return ++request
    }

    /** Plays what [options] ask for, giving the id of the track it starts on, or why it can't. */
    private suspend fun start(options: PlayOptions): Result<String> = loader.load(options).fold(
        onSuccess = { queue ->
            playback.play(queue.tracks, queue.startIndex, queue.startPositionMs)
            Result.success(queue.tracks[queue.startIndex].id)
        },
        onFailure = { Result.failure(it as? PlayerException ?: PlayerException.NetworkFailure(it)) },
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

    internal companion object {
        /** How long a book that's been started may take to be the one playing. */
        const val START_TIMEOUT_MS = 15_000L
        private const val MS_PER_MINUTE = 60_000L
        private const val PROGRESS_INTERVAL_MS = 250L
    }
}

/** Whether the player screen has a book to show, and whether one asked for is still on its way. */
data class PlayerPresence(val hasBook: Boolean, val starting: Boolean)

/** A book asked for: still [Loading] (or failed), or sent to the service to play from [Playing.trackId]. */
private sealed interface Starting {
    data object Loading : Starting
    data class Playing(val trackId: String) : Starting
}

private fun presenceOf(state: AudiobookPlaybackState, starting: Starting?) = PlayerPresence(
    hasBook = state.currentTrack != null,
    starting = when (starting) {
        null -> false
        Starting.Loading -> true
        is Starting.Playing -> state.currentTrack?.id != starting.trackId
    },
)

/** Where playback is in the current track, and how long the track is (0 until known). */
data class PlayerProgress(val positionMs: Long, val durationMs: Long)
