package org.jellyfin.mobile.player.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * [AudiobookPlaybackController] over any Media3 [Player]: a MediaController in the app, a
 * TestExoPlayer in tests. The player's playlist is the source of truth: the state is rebuilt from
 * its items on every player event, so it's right however playback started (the app, a voice
 * search). Must be used on the player's application thread.
 */
// Each method is one player call; the surface is the controller interface's.
@Suppress("TooManyFunctions")
class PlayerAudiobookController(
    private val player: Player,
    private val streamUrl: (itemId: String) -> String,
    /** A MediaController can't skip silence itself: the service does it on request. */
    private val setSkipSilence: (Boolean) -> Unit,
) : AudiobookPlaybackController {

    private val _state = MutableStateFlow(AudiobookPlaybackState())
    override val state: StateFlow<AudiobookPlaybackState> = _state.asStateFlow()

    override val positionMs: Long get() = player.currentPosition

    override val durationMs: Long
        get() = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: (_state.value.currentTrack?.durationMs ?: 0L)

    override val volume: Float get() = player.volume

    init {
        player.addListener(
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = syncFromPlayer()
            },
        )
    }

    override fun play(tracks: List<AudiobookTrack>, startIndex: Int, startPositionMs: Long) {
        _state.update { it.copy(error = null) }
        player.setMediaItems(tracks.map { it.toMediaItem(streamUrl) }, startIndex, startPositionMs)
        player.prepare()
        player.play()
        syncFromPlayer()
    }

    override fun play() {
        when (player.playbackState) {
            Player.STATE_ENDED -> player.seekTo(player.currentMediaItemIndex, 0L)
            Player.STATE_IDLE -> player.prepare()
            else -> Unit
        }
        player.play()
    }

    override fun pause() = player.pause()

    /** A finished book still "wants to play" to the player, so play starts it over rather than pausing. */
    override fun togglePlayPause() =
        if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) pause() else play()

    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    override fun rewind() = player.seekBack()

    override fun fastForward() = player.seekForward()

    override fun previousChapter() = player.seekToPrevious()

    override fun nextChapter() = player.seekToNext()

    override fun skipTo(index: Int) {
        if (index in 0 until player.mediaItemCount) player.seekTo(index, 0L)
    }

    override fun setSpeed(speed: Float) = player.setPlaybackSpeed(speed)

    override fun setSkipSilence(enabled: Boolean) = setSkipSilence.invoke(enabled)

    override fun setVolume(volume: Float) {
        player.volume = volume
    }

    override fun stop() {
        player.stop()
        player.clearMediaItems()
        syncFromPlayer()
    }

    /** Lets go of the player: in the app, the MediaController and its binding to the service. */
    fun release() = player.release()

    private fun syncFromPlayer() {
        val tracks = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).toAudiobookTrack() }
        _state.update { state ->
            state.copy(
                tracks = tracks,
                currentIndex = if (tracks.isEmpty()) 0 else player.currentMediaItemIndex,
                playWhenReady = player.playWhenReady,
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                isEnded = player.playbackState == Player.STATE_ENDED,
                // Kept while the player sits stopped by it; gone once it plays again
                error = player.playerError?.let { it.localizedMessage ?: it.errorCodeName }
                    ?: state.error.takeIf { player.playbackState == Player.STATE_IDLE },
            )
        }
    }
}
