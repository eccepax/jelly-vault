package org.jellyfin.mobile.livetv.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.jellyfin.mobile.livetv.domain.LiveChannel

/**
 * The channel being watched. Activity-scoped, so the stream (and the player) survive rotation;
 * when the activity finishes for good the stream is closed ([onCleared]).
 */
class WatchViewModel(
    streams: LiveStreams,
    closer: StreamCloser,
    val player: LivePlayer,
) : ViewModel() {

    private val playback = LivePlayback(streams, player, closer, viewModelScope)
    private val lineup = MutableStateFlow<Map<String, LiveChannel>>(emptyMap())
    private val _watching = MutableStateFlow(false)

    /** Whether the player screen is up. */
    val watching: StateFlow<Boolean> = _watching.asStateFlow()

    val state: StateFlow<WatchUiState> = combine(
        playback.state,
        lineup,
        player.videoStarted,
    ) { playback, lineup, started ->
        WatchUiState(playback = playback, channel = playback.channelId?.let(lineup::get), videoStarted = started)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        WatchUiState(PlaybackState.Idle, channel = null, videoStarted = false),
    )

    /** Watches [channel]; zapping moves through [lineup] (the channels the viewer was browsing). */
    fun watch(channel: LiveChannel, lineup: List<LiveChannel>) {
        this.lineup.value = lineup.associateBy { it.id } + (channel.id to channel)
        _watching.value = true
        playback.play(channel.id, lineup.map { it.id }.ifEmpty { listOf(channel.id) })
    }

    fun zap(step: Int) = playback.zap(step)

    fun retry() = playback.retry()

    /** Back to the channel list. */
    fun exit() {
        playback.stop()
        _watching.value = false
    }

    /** The app went to the background (not a rotation): frees the connection until [onForeground]. */
    fun onBackground() = playback.pause()

    fun onForeground() = playback.resume()

    // Public so tests can end the view model's life the way the framework does.
    public override fun onCleared() {
        playback.release()
        player.release()
    }

    private val PlaybackState.channelId: String?
        get() = when (this) {
            is PlaybackState.Tuning -> channelId
            is PlaybackState.Playing -> channelId
            is PlaybackState.Failed -> channelId
            PlaybackState.Idle -> null
        }
}
