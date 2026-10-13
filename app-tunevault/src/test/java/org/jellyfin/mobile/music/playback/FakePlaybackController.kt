package org.jellyfin.mobile.music.playback

import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.mobile.music.domain.PlayQueue
import org.jellyfin.mobile.music.domain.Track

/** Records the commands it receives; its state is set directly by tests. */
class FakePlaybackController : PlaybackController {
    override val state = MutableStateFlow(PlaybackState())
    override var positionMs: Long = 0L
    override var durationMs: Long = 0L

    val commands = mutableListOf<String>()

    override fun play(tracks: List<Track>, startIndex: Int) {
        commands += "play ${tracks.joinToString(",") { it.id }} from $startIndex"
        state.value = state.value.copy(queue = PlayQueue.of(tracks, startIndex), isPlaying = true)
    }

    override fun playShuffled(tracks: List<Track>) {
        commands += "playShuffled ${tracks.joinToString(",") { it.id }}"
    }

    override fun togglePlayPause() {
        commands += "togglePlayPause"
    }

    /** Every volume set, in order (only the last is current). */
    val volumes = mutableListOf<Float>()

    override val volume: Float get() = volumes.lastOrNull() ?: 1f

    override fun pause() {
        commands += "pause"
        state.value = state.value.copy(isPlaying = false)
    }

    override fun setVolume(volume: Float) {
        volumes += volume
    }

    override fun skipToNext() {
        commands += "skipToNext"
    }

    override fun skipToPrevious() {
        commands += "skipToPrevious"
    }

    override fun seekTo(positionMs: Long) {
        commands += "seekTo $positionMs"
    }

    override fun cycleRepeatMode() {
        commands += "cycleRepeatMode"
    }

    override fun toggleShuffle() {
        commands += "toggleShuffle"
    }

    override fun playNext(tracks: List<Track>) {
        commands += "playNext ${tracks.joinToString(",") { it.id }}"
        state.value = state.value.copy(queue = state.value.queue.playNext(tracks))
    }

    override fun addToQueue(tracks: List<Track>) {
        commands += "addToQueue ${tracks.joinToString(",") { it.id }}"
        state.value = state.value.copy(queue = state.value.queue.addToQueue(tracks))
    }

    override fun removeFromQueue(index: Int) {
        commands += "remove $index"
        state.value = state.value.copy(queue = state.value.queue.remove(index))
    }

    override fun moveInQueue(from: Int, to: Int) {
        commands += "move $from to $to"
        state.value = state.value.copy(queue = state.value.queue.move(from, to))
    }

    override fun skipTo(index: Int) {
        commands += "skipTo $index"
        state.value = state.value.copy(queue = state.value.queue.skipTo(index))
    }

    override fun stop() {
        commands += "stop"
        state.value = PlaybackState()
    }
}
