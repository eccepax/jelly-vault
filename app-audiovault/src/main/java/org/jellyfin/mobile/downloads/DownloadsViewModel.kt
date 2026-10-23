package org.jellyfin.mobile.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jellyfin.mobile.data.entity.DownloadEntity

/**
 * The downloads screen's list: tracks whose download is complete (see [AudiobookDownloads.finished]).
 * Downloading, removing and repairing run here too, in this view model's scope, so leaving the
 * screen that asked, or a rotation, doesn't cut them short. Track ids are in their dashed form.
 */
class DownloadsViewModel(
    finished: Flow<List<DownloadEntity>>,
    private val downloadTracks: suspend (trackIds: List<String>) -> Unit,
    private val removeTrack: suspend (trackId: String) -> Unit,
    private val repairDownloads: suspend () -> Unit,
) : ViewModel() {
    val downloads: StateFlow<List<DownloadEntity>> =
        finished.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private var repairing = false

    fun download(trackIds: List<String>) {
        viewModelScope.launch { downloadTracks(trackIds) }
    }

    fun remove(trackIds: List<String>) {
        viewModelScope.launch { trackIds.forEach { removeTrack(it) } }
    }

    // Kept by its track's id, not by DownloadEntity.itemId, which is the media source's
    fun remove(download: DownloadEntity) = remove(listOf(download.mediaSource.itemId.toString()))

    /** Fetches again any downloaded track whose file has gone; once, however often it's asked. */
    fun repairOnce() {
        if (repairing) return
        repairing = true
        viewModelScope.launch { repairDownloads() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
