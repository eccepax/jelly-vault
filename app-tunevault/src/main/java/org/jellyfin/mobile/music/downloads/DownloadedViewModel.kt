package org.jellyfin.mobile.music.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.jellyfin.mobile.common.downloads.GroupStatus
import org.jellyfin.mobile.music.playback.PlaybackController

data class DownloadedItem(val collection: DownloadedCollection, val status: GroupStatus)

/** The Downloaded tab: what's kept on the device, and shuffling all of it. */
class DownloadedViewModel(
    downloads: CollectionDownloads,
    private val playback: PlaybackController,
) : ViewModel() {

    val items: StateFlow<List<DownloadedItem>> =
        combine(downloads.collections, downloads.statuses) { collections, statuses ->
            collections.map { DownloadedItem(it, statuses[it.id] ?: GroupStatus.NotDownloaded) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val downloaded: StateFlow<Set<String>> =
        downloads.downloadedTrackIds.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Every song fully on the device, once each, shuffled. */
    fun shuffleAll() {
        val tracks = items.value.flatMap { it.collection.tracks }.distinctBy { it.id }.filter { it.id in downloaded.value }
        if (tracks.isNotEmpty()) playback.playShuffled(tracks)
    }
}
