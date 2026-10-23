package org.jellyfin.mobile.player.playback

import kotlinx.coroutines.flow.MutableStateFlow

/** Records commands; tests set [state], [positionMs] and [durationMs] to stand for the player. */
// Each method records one command; the surface is the controller interface's.
@Suppress("TooManyFunctions")
class FakeAudiobookController : AudiobookPlaybackController {
    override val state = MutableStateFlow(AudiobookPlaybackState())
    override var positionMs: Long = 0L
    override var durationMs: Long = 0L
    private var currentVolume = 1f
    override val volume: Float get() = currentVolume
    val commands = mutableListOf<String>()

    /** As the real service does, the queue asked for shows in [state] only once a test calls [playing]. */
    var playsLater = false

    /** Puts [tracks] in the queue, [index] current, as if they were playing. */
    fun playing(vararg tracks: AudiobookTrack, index: Int = 0, positionMs: Long = 0L, isPlaying: Boolean = true) {
        state.value = AudiobookPlaybackState(
            tracks = tracks.toList(),
            currentIndex = index,
            playWhenReady = isPlaying,
            isPlaying = isPlaying,
        )
        this.positionMs = positionMs
        durationMs = tracks.getOrNull(index)?.durationMs ?: 0L
    }

    override fun play(tracks: List<AudiobookTrack>, startIndex: Int, startPositionMs: Long) {
        commands += "play ${tracks.joinToString(",") { it.id }} from $startIndex at $startPositionMs"
        if (!playsLater) playing(*tracks.toTypedArray(), index = startIndex, positionMs = startPositionMs)
    }

    override fun play() {
        commands += "play"
        state.value = state.value.copy(playWhenReady = true, isPlaying = true)
    }

    override fun pause() {
        commands += "pause"
        state.value = state.value.copy(playWhenReady = false, isPlaying = false)
    }

    override fun togglePlayPause() = if (state.value.playWhenReady) pause() else play()

    override fun seekTo(positionMs: Long) {
        commands += "seekTo $positionMs"
        this.positionMs = positionMs
    }

    override fun rewind() {
        commands += "rewind"
    }

    override fun fastForward() {
        commands += "fastForward"
    }

    override fun previousChapter() {
        commands += "previousChapter"
    }

    override fun nextChapter() {
        commands += "nextChapter"
    }

    override fun skipTo(index: Int) {
        commands += "skipTo $index"
        state.value = state.value.copy(currentIndex = index)
        positionMs = 0L
    }

    override fun setSpeed(speed: Float) {
        commands += "speed $speed"
    }

    override fun setSkipSilence(enabled: Boolean) {
        commands += "skipSilence $enabled"
    }

    override fun setVolume(volume: Float) {
        currentVolume = volume
    }

    override fun stop() {
        commands += "stop"
        state.value = AudiobookPlaybackState()
    }
}
