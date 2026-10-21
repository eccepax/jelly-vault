package org.jellyfin.mobile.music.playback

import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.mobile.music.domain.Track
import org.jellyfin.mobile.player.session.DeferredConnection
import java.util.concurrent.Executor

/**
 * A [PlaybackController] usable immediately while the real one is still connecting (in the app: a
 * MediaController binding to the PlaybackService). Commands sent before the connection completes
 * are replayed in order once it does; if it fails they're dropped and logged (see [DeferredConnection]).
 */
class DeferredPlaybackController(
    connection: ListenableFuture<out PlaybackController>,
    callbackExecutor: Executor,
    private val scope: CoroutineScope,
) : PlaybackController {

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val connection = DeferredConnection(connection, callbackExecutor) { connected ->
        scope.launch { connected.state.collect { _state.value = it } }
    }
    private val delegate: PlaybackController? get() = this.connection.connected

    override val positionMs: Long get() = delegate?.positionMs ?: 0L

    override val durationMs: Long get() = delegate?.durationMs ?: 0L
    override val volume: Float get() = delegate?.volume ?: 1f

    private fun send(command: (PlaybackController) -> Unit) = connection.send(command)

    override fun play(tracks: List<Track>, startIndex: Int) = send { it.play(tracks, startIndex) }
    override fun playShuffled(tracks: List<Track>) = send { it.playShuffled(tracks) }
    override fun togglePlayPause() = send { it.togglePlayPause() }
    override fun pause() = send { it.pause() }
    override fun setVolume(volume: Float) = send { it.setVolume(volume) }
    override fun skipToNext() = send { it.skipToNext() }
    override fun skipToPrevious() = send { it.skipToPrevious() }
    override fun seekTo(positionMs: Long) = send { it.seekTo(positionMs) }
    override fun cycleRepeatMode() = send { it.cycleRepeatMode() }
    override fun toggleShuffle() = send { it.toggleShuffle() }
    override fun playNext(tracks: List<Track>) = send { it.playNext(tracks) }
    override fun addToQueue(tracks: List<Track>) = send { it.addToQueue(tracks) }
    override fun removeFromQueue(index: Int) = send { it.removeFromQueue(index) }
    override fun moveInQueue(from: Int, to: Int) = send { it.moveInQueue(from, to) }
    override fun skipTo(index: Int) = send { it.skipTo(index) }
    override fun stop() = send { it.stop() }
}
