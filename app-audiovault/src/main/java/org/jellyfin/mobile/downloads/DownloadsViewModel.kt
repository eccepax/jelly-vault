package org.jellyfin.mobile.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jellyfin.mobile.data.entity.DownloadEntity
import timber.log.Timber

/**
 * The downloads screen's list: tracks whose download is complete (see [AudiobookDownloads.finished]).
 * Downloading, removing and repairing run here too, in this view model's scope, so leaving the
 * screen that asked, or a rotation, doesn't cut them short. Track ids are in their dashed form.
 */
class DownloadsViewModel(
    finished: Flow<List<DownloadEntity>>,
    private val downloadTracks: suspend (trackIds: List<String>) -> Unit,
    private val removeTracks: suspend (trackIds: List<String>) -> Unit,
    private val repairDownloads: suspend () -> Unit,
) : ViewModel() {
    val downloads: StateFlow<List<DownloadEntity>> =
        finished.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private var repairing = false
    private var repaired = false

    fun download(trackIds: List<String>) {
        viewModelScope.launch { downloadTracks(trackIds) }
    }

    fun remove(trackIds: List<String>) {
        viewModelScope.launch { removeTracks(trackIds) }
    }

    // Kept by its track's id, not by DownloadEntity.itemId, which is the media source's
    fun remove(download: DownloadEntity) = remove(listOf(download.mediaSource.itemId.toString()))

    /**
     * Fetches again any downloaded track whose file has gone; once, however often it's asked. One
     * that fails (the server unreachable) is tried again when next asked.
     */
    fun repairOnce() {
        if (repairing || repaired) return
        repairing = true
        viewModelScope.launch {
            try {
                repairDownloads()
                repaired = true
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.w(e, "Couldn't repair the downloads")
            } finally {
                repairing = false
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
