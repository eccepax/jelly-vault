package org.jellyfin.mobile.music.downloads

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.scheduler.Requirements
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jellyfin.mobile.music.downloads.TrackDownload.State

/** How a Media3 [Download] reads to the rest of the app. */
@UnstableApi
fun Download.toTrackDownload(notMetRequirements: Int): TrackDownload {
    val mapped = when (state) {
        Download.STATE_COMPLETED -> State.DONE
        Download.STATE_FAILED -> State.FAILED
        Download.STATE_DOWNLOADING -> State.DOWNLOADING
        Download.STATE_STOPPED -> State.WAITING
        Download.STATE_QUEUED -> if (notMetRequirements != 0) State.WAITING else State.QUEUED
        else -> State.QUEUED
    }
    return TrackDownload(request.id, mapped, bytesDownloaded)
}

/**
 * [TrackDownloader] on Media3's [DownloadManager], which resumes interrupted downloads, waits for
 * its requirements (e.g. Wi-Fi) and survives restarts. Must be used on the main thread, like the
 * manager. [onWork] runs whenever there is something to fetch, to keep the download service up.
 */
@UnstableApi
class Media3TrackDownloader(
    private val manager: DownloadManager,
    scope: CoroutineScope,
    private val onWork: () -> Unit = {},
) : TrackDownloader {

    private val _downloads = MutableStateFlow<Map<String, TrackDownload>>(emptyMap())
    override val downloads: StateFlow<Map<String, TrackDownload>> = _downloads.asStateFlow()

    private val _isReady = MutableStateFlow(manager.isInitialized)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        manager.addListener(
            object : DownloadManager.Listener {
                override fun onInitialized(downloadManager: DownloadManager) {
                    refresh()
                    _isReady.value = true
                }
                override fun onDownloadChanged(manager: DownloadManager, download: Download, e: Exception?) = refresh()
                override fun onDownloadRemoved(manager: DownloadManager, download: Download) = refresh()
                override fun onRequirementsStateChanged(manager: DownloadManager, r: Requirements, notMet: Int) =
                    refresh()
            },
        )
        if (manager.isInitialized) refresh()
        // The manager only reports state changes; while songs are downloading, poll for progress.
        scope.launch {
            while (isActive) {
                delay(PROGRESS_INTERVAL_MS)
                if (manager.currentDownloads.isNotEmpty()) refresh()
            }
        }
    }

    override fun download(trackId: String, uri: String) {
        manager.addDownload(DownloadRequest.Builder(trackId, Uri.parse(uri)).build())
        onWork()
    }

    override fun remove(trackId: String) {
        manager.removeDownload(trackId)
    }

    private fun refresh() {
        val notMet = manager.notMetRequirements
        val all = mutableMapOf<String, TrackDownload>()
        manager.downloadIndex.getDownloads().use { cursor ->
            while (cursor.moveToNext()) {
                val download = cursor.download
                if (download.state != Download.STATE_REMOVING) all[download.request.id] = download.toTrackDownload(notMet)
            }
        }
        // The index lags a little behind downloads in flight; the manager's own copies are current.
        manager.currentDownloads.forEach { download ->
            if (download.state != Download.STATE_REMOVING) {
                all[download.request.id] = download.toTrackDownload(notMet)
            } else {
                all.remove(download.request.id)
            }
        }
        _downloads.value = all
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 1_000L
    }
}
