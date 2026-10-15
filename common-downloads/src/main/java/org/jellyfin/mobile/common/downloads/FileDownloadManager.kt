package org.jellyfin.mobile.common.downloads

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.Downloader
import androidx.media3.exoplayer.offline.DownloaderFactory
import androidx.media3.exoplayer.scheduler.Requirements

private const val PARALLEL_DOWNLOADS = 2

/**
 * Media3's download index table for these downloads. Not the default one: older builds kept
 * Media3-cache downloads there, which would read as finished files that don't exist.
 */
private const val INDEX_NAME = "files"

/** Wi-Fi only means any unmetered network; otherwise any network at all. */
@UnstableApi
fun downloadRequirements(wifiOnly: Boolean) =
    Requirements(if (wifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)

/** Makes a [FileDownloader] for each request, fetching through [dataSourceFactory] into [files]. */
@UnstableApi
class FileDownloaderFactory(
    private val files: DownloadFiles,
    private val dataSourceFactory: DataSource.Factory,
) : DownloaderFactory {
    override fun createDownloader(request: DownloadRequest): Downloader =
        FileDownloader(request.uri, files.fileOf(request.id), dataSourceFactory.createDataSource())
}

/**
 * A Media3 [DownloadManager] that saves each item as a plain file in [files]. [dataSourceFactory]
 * fetches from the server (adding the access token is up to it). The manager queues downloads,
 * retries failures [retries] times, waits for its requirements and survives restarts.
 *
 * Media3 starts a manager paused and leaves resuming to its DownloadService; this one starts
 * downloading straight away, so downloads also run while the app is open but the service couldn't
 * be started (Android refuses that from the background).
 */
@UnstableApi
fun fileDownloadManager(
    context: Context,
    databaseProvider: DatabaseProvider,
    dataSourceFactory: DataSource.Factory,
    files: DownloadFiles,
    retries: Int = DownloadManager.DEFAULT_MIN_RETRY_COUNT,
    requirements: Requirements = downloadRequirements(wifiOnly = false),
): DownloadManager = DownloadManager(
    context,
    DefaultDownloadIndex(databaseProvider, INDEX_NAME),
    FileDownloaderFactory(files, dataSourceFactory),
).apply {
    maxParallelDownloads = PARALLEL_DOWNLOADS
    minRetryCount = retries
    this.requirements = requirements
    resumeDownloads()
}
