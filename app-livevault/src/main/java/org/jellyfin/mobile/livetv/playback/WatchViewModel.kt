package org.jellyfin.mobile.livetv.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jellyfin.mobile.cast.LiveCasting
import org.jellyfin.mobile.livetv.channels.castTitle
import org.jellyfin.mobile.livetv.domain.LiveChannel
import org.jellyfin.mobile.livetv.viewing.ViewingMemory
import timber.log.Timber

/**
 * The channel being watched, here on [localPlayer] or on the cast device while one is connected
 * ([cast]). Activity-scoped, so the stream (and the player) survive rotation; when the activity
 * finishes for good the stream is closed ([onCleared]). While a channel plays on a cast device the
 * app is kept alive ([keepAlive]), so the stream is still reported, and closed, from the background.
 */
class WatchViewModel(
    streams: LiveStreams,
    closer: StreamCloser,
    val localPlayer: LivePlayer,
    private val cast: LiveCasting,
    private val sessions: LiveTvSessions,
    private val memory: ViewingMemory,
    private val keepAlive: CastKeepAlive = CastKeepAlive.None,
) : ViewModel() {

    private val lineup = MutableStateFlow<Map<String, LiveChannel>>(emptyMap())
    private val player = CastingLivePlayer(localPlayer, cast, describe = { lineup.value[it] })
    private val _watching = MutableStateFlow(false)

    // Stopped on the cast device itself, the viewer stopped watching: back to the channels
    private val playback = LivePlayback(
        streams,
        player,
        closer,
        viewModelScope,
        onStoppedOnCastDevice = { _watching.value = false },
    )

    /** Whether the player screen is up. */
    val watching: StateFlow<Boolean> = _watching.asStateFlow()

    /**
     * When a channel that needs the provider login fails, the likely reason: the login (one stream
     * at a time) is in use elsewhere.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val loginInUse: Flow<LoginInUse?> = playback.state.mapLatest { state ->
        val failed = state as? PlaybackState.Failed ?: return@mapLatest null
        if (lineup.value[failed.channelId]?.usesProviderLogin != true) return@mapLatest null
        LoginInUse(holders = loginHolders())
    }

    val state: StateFlow<WatchUiState> = combine(
        playback.state,
        lineup,
        player.videoStarted,
        loginInUse,
        // Casting once connected, named or not (until then the channel still plays here)
        combine(cast.connected, cast.deviceName) { connected, name -> connected to name.takeIf { connected } },
    ) { playback, lineup, started, loginInUse, (casting, castDevice) ->
        WatchUiState(
            playback = playback,
            channel = playback.channelId?.let(lineup::get),
            videoStarted = started,
            loginInUse = loginInUse,
            castDevice = castDevice,
            casting = casting,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        WatchUiState(PlaybackState.Idle, channel = null, videoStarted = false),
    )

    init {
        viewModelScope.launch {
            combine(playback.state, cast.connected, cast.deviceName, lineup) { state, connected, device, lineup ->
                val channelId = state.channelId?.takeIf { connected && state !is PlaybackState.Failed }
                channelId?.let { id -> (lineup[id]?.castTitle ?: id) to device }
            }.distinctUntilChanged().collect { casting ->
                if (casting == null) keepAlive.release() else keepAlive.hold(casting.first, casting.second)
            }
        }
        // Where the viewer actually ended up watching: a zap's landing, not a channel that failed.
        viewModelScope.launch {
            playback.state.collect { state ->
                if (state is PlaybackState.Playing) memory.rememberChannel(state.channelId)
            }
        }
    }

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

    fun onForeground() {
        // Android doesn't let a cast connecting in the background keep the app alive: it's asked again here
        keepAlive.holdAgain()
        playback.resume()
    }

    /** Ends the cast; the channel comes back here (see [LivePlayback]). */
    fun stopCasting() = cast.stopCasting()

    /** Who on the server is holding the login; none known when the server can't say. */
    private suspend fun loginHolders(): List<ChannelSession> = try {
        sessions.othersWatching().holdingLogin()
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Timber.w(e, "Couldn't read the server's sessions")
        emptyList()
    }

    // Public so tests can end the view model's life the way the framework does.
    public override fun onCleared() {
        playback.release()
        player.release()
        keepAlive.release()
    }
}
