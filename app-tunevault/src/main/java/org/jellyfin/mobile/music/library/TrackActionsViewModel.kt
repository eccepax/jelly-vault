package org.jellyfin.mobile.music.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.jellyfin.mobile.music.domain.Track
import org.jellyfin.mobile.music.downloads.CollectionDownloads
import org.jellyfin.mobile.music.playback.PlaybackController
import org.jellyfin.mobile.music.playback.currentTrackId
import org.jellyfin.mobile.music.playlists.AddToPlaylistViewModel
import org.koin.compose.viewmodel.koinViewModel

/** The long-press actions every song row offers. One place, so all lists behave the same. */
class TrackActionsViewModel(
    private val playback: PlaybackController,
    downloads: CollectionDownloads,
) : ViewModel() {
    val downloadedTrackIds: StateFlow<Set<String>> =
        downloads.downloadedTrackIds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBED_MS), emptySet())

    fun playNext(track: Track) = playback.playNext(listOf(track))
    fun addToQueue(track: Track) = playback.addToQueue(listOf(track))
}

/** [TrackListActions] wired to the real queue and the playing track. */
@Composable
fun trackListActions(
    actions: TrackActionsViewModel = koinViewModel(),
    addToPlaylist: AddToPlaylistViewModel = koinViewModel(),
) = TrackListActions(
    onPlayNext = actions::playNext,
    onAddToQueue = actions::addToQueue,
    currentTrackId = currentTrackId(),
    onAddToPlaylist = addToPlaylist::request,
    downloadedTrackIds = actions.downloadedTrackIds.collectAsState().value,
)

private const val SUBSCRIBED_MS = 5_000L
