package org.jellyfin.mobile.player.playback

import kotlinx.coroutines.flow.StateFlow

data class AudiobookPlaybackState(
    /** The book's tracks, in order; empty when nothing is loaded. */
    val tracks: List<AudiobookTrack> = emptyList(),
    val currentIndex: Int = 0,
    /** Whether it's meant to be playing: what the play button shows, steady while buffering. */
    val playWhenReady: Boolean = false,
    /** Whether sound is coming out right now. */
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    /** The last track played to its end, and playback stopped there. */
    val isEnded: Boolean = false,
    /** Why playback stopped, until another book starts; null when it didn't fail. */
    val error: String? = null,
) {
    val currentTrack: AudiobookTrack? get() = tracks.getOrNull(currentIndex)
    val hasNext: Boolean get() = currentIndex < tracks.lastIndex
}

/**
 * What the app drives playback through: the player screen, the mini player, the sleep timer,
 * progress reporting. Tested against fakes of this; in the app it's the playback service's player,
 * through a MediaController.
 */
// One cohesive transport surface (the same controls a lock screen offers); splitting it would only
// scatter a single concept across interfaces.
@Suppress("TooManyFunctions")
interface AudiobookPlaybackController {
    val state: StateFlow<AudiobookPlaybackState>

    /** Where playback is in the current track; read on demand (it changes continuously). */
    val positionMs: Long

    /** The current track's length: the player's once it knows it, the server's until then. */
    val durationMs: Long

    /** The player's volume, 0 to 1 (the sleep timer fades it out). */
    val volume: Float

    /** Plays [tracks], starting [startPositionMs] into the one at [startIndex]. */
    fun play(tracks: List<AudiobookTrack>, startIndex: Int, startPositionMs: Long)
    fun play()
    fun pause()
    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun rewind()
    fun fastForward()
    fun previousChapter()
    fun nextChapter()

    /** Plays the track at [index] from its start. */
    fun skipTo(index: Int)
    fun setSpeed(speed: Float)
    fun setSkipSilence(enabled: Boolean)
    fun setVolume(volume: Float)

    /** Stops playback and empties the queue (e.g. the mini player's stop, or signing out). */
    fun stop()
}
