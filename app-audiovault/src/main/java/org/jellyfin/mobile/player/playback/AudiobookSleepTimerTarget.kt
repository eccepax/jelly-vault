package org.jellyfin.mobile.player.playback

import org.jellyfin.mobile.common.playback.PlaybackSample
import org.jellyfin.mobile.common.playback.SleepTimerTarget

/**
 * AudioVault's player as the shared sleep timer sees it: each chapter is a segment, so "end of
 * segment" is the end of the chapter. A track without chapters is one segment.
 */
class AudiobookSleepTimerTarget(private val controller: AudiobookPlaybackController) : SleepTimerTarget {

    override fun sample(): PlaybackSample {
        val state = controller.state.value
        val track = state.currentTrack
        val positionMs = controller.positionMs
        val starts = track?.chapters.orEmpty().map { it.startMs }
        val chapter = starts.indexOfLast { it <= positionMs }
        if (track == null || chapter < 0) {
            return PlaybackSample(track?.id, positionMs, controller.durationMs, state.isPlaying, state.hasNext)
        }
        val start = starts[chapter]
        val end = starts.getOrNull(chapter + 1) ?: controller.durationMs
        return PlaybackSample(
            segmentId = "${state.currentIndex}/${track.id}/$chapter",
            positionMs = positionMs - start,
            durationMs = (end - start).coerceAtLeast(0L),
            isPlaying = state.isPlaying,
            hasNext = chapter < starts.lastIndex || state.hasNext,
        )
    }

    override fun pause() = controller.pause()

    override fun rewindToSegmentStart() {
        val positionMs = controller.positionMs
        val start = controller.state.value.currentTrack?.chapters.orEmpty().lastOrNull { it.startMs <= positionMs }?.startMs
        controller.seekTo(start ?: 0L)
    }

    override fun setFade(fade: Float) = controller.setVolume(fade)
}
