package org.jellyfin.mobile.music.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import org.jellyfin.mobile.music.catalog.FavoritesRepository
import org.jellyfin.mobile.music.domain.RepeatMode
import org.jellyfin.mobile.music.domain.Track
import org.jellyfin.mobile.music.playback.PlaybackController
import org.jellyfin.mobile.music.playback.PlaybackState

data class NowPlayingUiState(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    /** Asked to play, and waiting for the stream: the play button shows it's busy. */
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isShuffled: Boolean = false,
    val isFavorite: Boolean = false,
) {
    /** How far through the track, 0 to 1. */
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** Backs both the mini-player and the Now Playing screen. */
@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingViewModel(
    private val playback: PlaybackController,
    private val favorites: FavoritesRepository,
) : ViewModel() {

    private val seeks = MutableSharedFlow<Long>(extraBufferCapacity = 1)

    /**
     * Polls the player's position every [TICK_MS] while playing; reads it once when paused or when
     * the track changes. Seeks show up immediately rather than on the next tick.
     */
    private val position = merge(
        playback.state
            .map { it.isPlaying to it.queue.entries.getOrNull(it.queue.currentIndex)?.uid }
            .distinctUntilChanged()
            .flatMapLatest { (isPlaying, _) ->
                if (isPlaying) {
                    flow {
                        while (true) {
                            emit(playback.positionMs)
                            delay(TICK_MS)
                        }
                    }
                } else {
                    flowOf(playback.positionMs)
                }
            },
        seeks,
    )

    val state: StateFlow<NowPlayingUiState> = combine(
        playback.state,
        position,
        favorites.overrides,
    ) { state, position, _ ->
        toUiState(state, position)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = toUiState(playback.state.value, playback.positionMs),
        )

    fun togglePlayPause() = playback.togglePlayPause()
    fun skipToNext() = playback.skipToNext()
    fun skipToPrevious() = playback.skipToPrevious()
    fun cycleRepeatMode() = playback.cycleRepeatMode()
    fun toggleShuffle() = playback.toggleShuffle()

    fun toggleFavorite() {
        val track = playback.state.value.currentTrack ?: return
        favorites.setFavorite(track.id, !favorites.isFavorite(track.id, track.isFavorite))
    }

    fun seekTo(positionMs: Long) {
        playback.seekTo(positionMs)
        seeks.tryEmit(positionMs)
    }

    private fun toUiState(playbackState: PlaybackState, positionMs: Long) = NowPlayingUiState(
        track = playbackState.currentTrack,
        isPlaying = playbackState.isPlaying,
        isBuffering = playbackState.isBuffering,
        positionMs = positionMs,
        // The server may not know a file's length (0); the player learns it once the stream loads.
        durationMs = playbackState.currentTrack?.durationMs?.takeIf { it > 0 } ?: playback.durationMs,
        repeatMode = playbackState.queue.repeatMode,
        isShuffled = playbackState.queue.isShuffled,
        isFavorite = playbackState.currentTrack?.let { favorites.isFavorite(it.id, it.isFavorite) } ?: false,
    )

    companion object {
        const val TICK_MS = 500L
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
