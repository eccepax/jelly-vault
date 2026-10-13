package org.jellyfin.mobile.music.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.jellyfin.mobile.music.domain.Track
import org.jellyfin.mobile.music.playback.PlaybackController
import org.jellyfin.mobile.music.playback.PlaybackState

/** One queue entry; [index] is its absolute position in the queue (what queue edits take). */
data class QueueRow(val index: Int, val track: Track)

/** The playing track and what's up next; tracks already played aren't shown. */
data class QueueUiState(
    val nowPlaying: QueueRow? = null,
    val upNext: List<QueueRow> = emptyList(),
)

class QueueViewModel(private val playback: PlaybackController) : ViewModel() {

    val state: StateFlow<QueueUiState> = playback.state
        .map(::toUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), toUiState(playback.state.value))

    fun remove(index: Int) = playback.removeFromQueue(index)
    fun move(from: Int, to: Int) = playback.moveInQueue(from, to)
    fun skipTo(index: Int) = playback.skipTo(index)

    private fun toUiState(state: PlaybackState): QueueUiState {
        val queue = state.queue
        val current = queue.current ?: return QueueUiState()
        return QueueUiState(
            nowPlaying = QueueRow(queue.currentIndex, current),
            upNext = queue.tracks.withIndex().drop(
                queue.currentIndex + 1,
            ).map { (index, track) -> QueueRow(index, track) },
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
