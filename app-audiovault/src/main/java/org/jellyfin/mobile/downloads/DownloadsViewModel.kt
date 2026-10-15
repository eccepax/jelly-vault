package org.jellyfin.mobile.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.jellyfin.mobile.data.entity.DownloadEntity

/** The downloads screen's list: tracks whose download is complete (see [AudiobookDownloads.finished]). */
class DownloadsViewModel(finished: Flow<List<DownloadEntity>>) : ViewModel() {
    val downloads: StateFlow<List<DownloadEntity>> =
        finished.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
