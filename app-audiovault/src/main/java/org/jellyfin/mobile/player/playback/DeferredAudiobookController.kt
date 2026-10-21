package org.jellyfin.mobile.player.playback

import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.mobile.player.session.DeferredConnection
import java.util.concurrent.Executor

/**
 * An [AudiobookPlaybackController] usable immediately while the real one is still connecting (in
 * the app: a MediaController binding to the AudiobookPlaybackService). It connects on the first
 * command, or when the service starts and asks it to [connect]: connecting starts the service and
 * its player, not worth it just to follow the state. Commands sent before the connection completes
 * are replayed in order once it does; once it's [disconnected], the next command connects again
 * (see [DeferredConnection]).
 */
// Each method forwards one command; the surface is the controller interface's.
@Suppress("TooManyFunctions")
class DeferredAudiobookController(
    connect: () -> ListenableFuture<out AudiobookPlaybackController>,
    callbackExecutor: Executor,
    private val scope: CoroutineScope,
) : AudiobookPlaybackController {

    private val _state = MutableStateFlow(AudiobookPlaybackState())
    override val state: StateFlow<AudiobookPlaybackState> = _state.asStateFlow()

    private var following: Job? = null
    private val connection = DeferredConnection(connect, callbackExecutor) { connected ->
        following = scope.launch { connected.state.collect { _state.value = it } }
    }
    private val delegate: AudiobookPlaybackController? get() = this.connection.connected

    override val positionMs: Long get() = delegate?.positionMs ?: 0L
    override val durationMs: Long get() = delegate?.durationMs ?: 0L
    override val volume: Float get() = delegate?.volume ?: 1f

    fun connect() = connection.connect()

    /** The connection was lost, and what was playing with it: nothing is now. */
    fun disconnected() {
        connection.disconnected()
        following?.cancel()
        following = null
        _state.value = AudiobookPlaybackState()
    }

    private fun send(command: (AudiobookPlaybackController) -> Unit) = connection.send(command)

    override fun play(tracks: List<AudiobookTrack>, startIndex: Int, startPositionMs: Long) =
        send { it.play(tracks, startIndex, startPositionMs) }
    override fun play() = send { it.play() }
    override fun pause() = send { it.pause() }
    override fun togglePlayPause() = send { it.togglePlayPause() }
    override fun seekTo(positionMs: Long) = send { it.seekTo(positionMs) }
    override fun rewind() = send { it.rewind() }
    override fun fastForward() = send { it.fastForward() }
    override fun previousChapter() = send { it.previousChapter() }
    override fun nextChapter() = send { it.nextChapter() }
    override fun skipTo(index: Int) = send { it.skipTo(index) }
    override fun setSpeed(speed: Float) = send { it.setSpeed(speed) }
    override fun setSkipSilence(enabled: Boolean) = send { it.setSkipSilence(enabled) }
    override fun setVolume(volume: Float) = send { it.setVolume(volume) }
    override fun stop() = send { it.stop() }
}
