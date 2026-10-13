package org.jellyfin.mobile.downloads

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION_CODES.P
import android.util.AndroidException
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.common.data.dao.DownloadRecordDao
import org.jellyfin.mobile.common.data.entity.DownloadRecord
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.downloads.DownloadServiceUtil.getDownloadTracker
import org.jellyfin.mobile.player.deviceprofile.DeviceProfileBuilder
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.jellyfin.mobile.player.source.MediaSourceResolver
import org.jellyfin.mobile.utils.AndroidVersion
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.extractId
import org.jellyfin.mobile.utils.requestPermission
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.serializer.toUUID
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class DownloadUtils(
    val context: Context,
    private val filename: String,
    private val downloadURL: String,
    private val downloadMethod: Int,
) : KoinComponent {
    private val mainActivity: MainActivity = context as MainActivity
    private val downloadFolder: File
    private val itemId: String
    private val itemUUID: UUID
    private val contentId: String
    private val downloadDao: DownloadDao by inject()
    private val apiClient: ApiClient = get()
    private val mediaSourceResolver: MediaSourceResolver by inject()
    private val deviceProfileBuilder: DeviceProfileBuilder by inject()
    private val deviceProfile = deviceProfileBuilder.getDeviceProfile()
    private val notificationManager: NotificationManager? by lazy { context.getSystemService() }
    private val connectivityManager: ConnectivityManager? by lazy { context.getSystemService() }
    private val appPreferences: AudioVaultAppPreferences by inject()
    private var downloadTracker: DownloadTracker = getDownloadTracker()
    private val downloadRecordScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        itemId = extractItemId(downloadURL)
            ?: throw IllegalArgumentException("Download URL missing item id: $downloadURL")
        itemUUID = itemId.toUUID()
        contentId = itemUUID.toString()
        downloadFolder = File(context.filesDir, "/Downloads/$itemId/")
        downloadFolder.mkdirs()
    }

    suspend fun download() {
        createDownloadNotificationChannel()
        checkForDownloadMethod()
        val jellyfinMediaSource = retrieveJellyfinMediaSource()
        val isDestinationInternal = jellyfinMediaSource.getIsDestinationInternal()
        if (isDestinationInternal) {
            if (checkIfDownloadExists(itemId)) {
                removeDownloadRemains(jellyfinMediaSource)
            } else {
                downloadFiles(jellyfinMediaSource)
            }
        } else {
            downloadExternalMediaFile(jellyfinMediaSource)
        }
    }

    @SuppressLint("InlinedApi")
    private fun checkForDownloadMethod() {
        if (downloadMethod == DownloadMethod.WIFI_ONLY) setUnmeteredRequirement()
        val isRoaming = if (downloadMethod == DownloadMethod.MOBILE_DATA) {
            if (Build.VERSION.SDK_INT < P) isNetworkRoamingCompat() else isNetworkRoaming()
        } else {
            false
        }

        if (!isValidDownloadConnection(downloadMethod, isNetworkMetered(), isRoaming)) {
            throw IOException(context.getString(R.string.failed_network_method_check))
        }
    }

    private fun setUnmeteredRequirement() {
        DownloadService.sendSetRequirements(
            context,
            JellyfinDownloadService::class.java,
            Requirements(Requirements.NETWORK_UNMETERED),
            false,
        )
    }

    private fun isNetworkMetered(): Boolean = connectivityManager?.isActiveNetworkMetered == true

    private fun isNetworkRoamingCompat(): Boolean = connectivityManager?.activeNetworkInfo?.isRoaming ?: throw AndroidException()

    @RequiresApi(P)
    private fun isNetworkRoaming(): Boolean {
        val network: Network = connectivityManager?.activeNetwork ?: throw AndroidException()
        val capabilities: NetworkCapabilities = connectivityManager?.getNetworkCapabilities(network) ?: throw AndroidException()
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
    }

    private suspend fun checkIfDownloadExists(itemId: String) = downloadDao.downloadExists(itemId)

    private suspend fun retrieveJellyfinMediaSource() = mediaSourceResolver.resolveMediaSource(
        itemId = itemUUID,
        mediaSourceId = itemId,
        deviceProfile = deviceProfile,
    ).getOrElse { throw IOException(context.getString(R.string.failed_information)) }

    // Download shows, movies, and audio content to internal storage
    private fun JellyfinMediaSource.getIsDestinationInternal() =
        isDestinationInternal(appPreferences.downloadToInternal, item?.type)

    private suspend fun downloadFiles(jellyfinMediaSource: JellyfinMediaSource) {
        val jellyfinDownloadTracker = JellyfinDownloadTracker(jellyfinMediaSource)
        downloadTracker.addListener(jellyfinDownloadTracker)
        // Only reached for a track that's actually about to start downloading (not the toggle's
        // remove-existing-download branch) — see DownloadBatchTracker for why this is keyed here.
        DownloadBatchTracker.trackStarted(filename, contentId)
        downloadMediaFile(jellyfinMediaSource)
        downloadExternalSubtitles(jellyfinMediaSource)
    }

    private fun downloadMediaFile(jellyfinMediaSource: JellyfinMediaSource) {
        val downloadUri = downloadURL.toUri()
        val cacheKey = downloadURL.toUri().extractId()

        val downloadRequest = DownloadRequest.Builder(contentId, downloadUri)
            .setData(jellyfinMediaSource.item!!.name!!.encodeToByteArray())
            .setCustomCacheKey(cacheKey)
            .build()
        DownloadService.sendAddDownload(
            context,
            JellyfinDownloadService::class.java,
            downloadRequest,
            false,
        )
    }

    private fun downloadExternalSubtitles(jellyfinMediaSource: JellyfinMediaSource) {
        jellyfinMediaSource.externalSubtitleStreams.forEach {
            val subtitleDownloadURL = apiClient.createUrl(it.deliveryUrl).toUri()
            val subtitleCacheKey: String = subtitleDownloadURL.extractId()

            val downloadRequest = DownloadRequest.Builder("$contentId:${it.index}", subtitleDownloadURL)
                .setCustomCacheKey(subtitleCacheKey)
                .build()
            DownloadService.sendAddDownload(
                context,
                JellyfinDownloadService::class.java,
                downloadRequest,
                false,
            )
        }
    }

    private suspend fun storeDownloadSpecs(jellyfinMediaSource: JellyfinMediaSource) =
        LocalJellyfinMediaSource(
            jellyfinMediaSource,
            downloadFolder.canonicalPath,
            downloadURL,
            downloadTracker.getDownloadSize(downloadURL.toUri()),
        ).also { localSource ->
            downloadDao.insert(DownloadEntity(localSource))
            get<DownloadRecordDao>().insert(
                DownloadRecord(
                    itemId = contentId,
                    title = jellyfinMediaSource.item?.name ?: "",
                    filePath = downloadFolder.canonicalPath,
                    fileSizeBytes = localSource.downloadSize,
                    downloadedAt = System.currentTimeMillis(),
                    mediaType = "AUDIO",
                ),
            )
        }

    private suspend fun downloadExternalMediaFile(jellyfinMediaSource: JellyfinMediaSource) {
        if (!AndroidVersion.isAtLeastQ) {
            @Suppress("MagicNumber")
            val granted = withTimeout(2 * 60 * 1000) {
                suspendCoroutine { continuation ->
                    mainActivity.requestPermission(WRITE_EXTERNAL_STORAGE) { requestPermissionsResult ->
                        continuation.resume(requestPermissionsResult[WRITE_EXTERNAL_STORAGE] == PERMISSION_GRANTED)
                    }
                }
            }

            if (!granted) {
                throw IOException(context.getString(R.string.download_no_storage_permission))
            }
        }

        val downloadRequest = DownloadManager.Request(downloadURL.toUri())
            .setTitle(jellyfinMediaSource.getName(context))
            .setDescription(context.getString(R.string.downloading))
            .setDestinationUri(Uri.fromFile(File(appPreferences.downloadLocation, filename)))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        context.getSystemService<DownloadManager>()?.enqueue(downloadRequest)
    }

    private fun removeDownloadRemains(jellyfinMediaSource: JellyfinMediaSource) {
        downloadRecordScope.launch { get<DownloadRecordDao>().delete(contentId) }
        downloadFolder.deleteRecursively()

        DownloadService.sendRemoveDownload(
            context,
            JellyfinDownloadService::class.java,
            contentId,
            false,
        )

        jellyfinMediaSource.externalSubtitleStreams.forEach {
            DownloadService.sendRemoveDownload(
                context,
                JellyfinDownloadService::class.java,
                "$contentId:${it.index}",
                false,
            )
        }
    }

    private fun createDownloadNotificationChannel() {
        if (AndroidVersion.isAtLeastO) {
            val notificationChannel = NotificationChannel(
                Constants.DOWNLOAD_NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.downloads),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.download_notifications_description)
            }
            notificationManager?.createNotificationChannel(notificationChannel)
        }
    }

    private inner class JellyfinDownloadTracker(val jellyfinMediaSource: JellyfinMediaSource) : DownloadTracker.Listener {
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        override fun onDownloadsChanged() {
            if (downloadTracker.isDownloaded(downloadURL.toUri())) {
                scope.launch {
                    storeDownloadSpecs(jellyfinMediaSource)
                }
                downloadTracker.removeListener(this)
            } else if (downloadTracker.isFailed(downloadURL.toUri())) {
                removeDownloadRemains(jellyfinMediaSource)
                downloadTracker.removeListener(this)
            }
        }
    }
}
