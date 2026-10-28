package org.jellyfin.mobile.music.playback

import kotlinx.coroutines.flow.StateFlow
import org.jellyfin.mobile.music.domain.PlayQueue
import org.jellyfin.mobile.music.domain.Track

data class PlaybackState(
    val queue: PlayQueue = PlayQueue.EMPTY,
    val isPlaying: Boolean = false,
    /** Asked to play, and waiting for the stream (a cast device starting a song, say). */
    val isBuffering: Boolean = false,
) {
    val currentTrack: Track? get() = queue.current
}

/** What the UI drives playback through. ViewModels are tested against a fake of this. */
// One cohesive transport surface (the same controls a lock screen offers); splitting it would only
// scatter a single concept across interfaces.
@Suppress("TooManyFunctions")
interface PlaybackController {
    val state: StateFlow<PlaybackState>

    /** Current position in the current track; read on demand (it changes continuously). */
    val positionMs: Long

    /** The current track's length as the player knows it from the stream; 0 until known. */
    val durationMs: Long

    fun play(tracks: List<Track>, startIndex: Int = 0)
    fun playShuffled(tracks: List<Track>)
    fun togglePlayPause()

    /** Pauses; does nothing if already paused. */
    fun pause()

    /** The player's volume, 0 to 1 (volume levelling times the sleep timer's fade). */
    val volume: Float

    fun setVolume(volume: Float)
    fun skipToNext()

    /** Restarts the current track if it's past [RESTART_THRESHOLD_MS], otherwise goes back one. */
    fun skipToPrevious()
    fun seekTo(positionMs: Long)
    fun cycleRepeatMode()
    fun toggleShuffle()

    /** Queues [tracks] right after the playing track. */
    fun playNext(tracks: List<Track>)

    /** Queues [tracks] at the end. On an empty queue this starts playing them. */
    fun addToQueue(tracks: List<Track>)

    fun removeFromQueue(index: Int)
    fun moveInQueue(from: Int, to: Int)

    /** Jumps to the queue entry at [index] and plays it from the start. */
    fun skipTo(index: Int)

    /** Stops playback and empties the queue (e.g. on sign-out). */
    fun stop()

    companion object {
        const val RESTART_THRESHOLD_MS = 3_000L
    }
}
