package org.jellyfin.mobile.music.playback

import org.jellyfin.mobile.common.playback.PlaybackSample
import org.jellyfin.mobile.common.playback.SleepTimerTarget

/**
 * TuneVault's player as the shared sleep timer sees it: each song is a segment, so "end of segment"
 * is the end of the song. Fading goes through [volume], shared with volume levelling, so a fade
 * starts from the levelled volume.
 */
class PlaybackSleepTimerTarget(
    private val playback: PlaybackController,
    private val volume: PlaybackVolume,
) : SleepTimerTarget {

    override fun sample(): PlaybackSample {
        val state = playback.state.value
        val entry = state.queue.entries.getOrNull(state.queue.currentIndex)
        // The stream's own length when the player knows it: a track's listed length can be off by seconds
        val durationMs = playback.durationMs.takeIf { it > 0 } ?: entry?.track?.durationMs ?: 0L
        return PlaybackSample(entry?.uid, playback.positionMs, durationMs, state.isPlaying, state.queue.hasNext)
    }

    override fun pause() = playback.pause()

    override fun rewindToSegmentStart() = playback.seekTo(0L)

    override fun setFade(fade: Float) = volume.setFade(fade)
}
