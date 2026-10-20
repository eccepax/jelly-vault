package org.jellyfin.mobile.utils

import android.Manifest
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.getSystemService
import androidx.media3.exoplayer.offline.DownloadManager
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.downloads.AudiobookDownloads
import org.jellyfin.mobile.downloads.DownloadMethod
import org.jellyfin.mobile.downloads.applyDownloadMethod
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.koin.android.ext.android.get
import kotlin.coroutines.resume

/**
 * Downloads a book's tracks. The network preference (asked the first time, then kept) and the
 * notification permission are settled once for the whole book, not once per track.
 */
suspend fun MainActivity.requestDownload(trackIds: List<String>) {
    if (trackIds.isEmpty()) return

    val appPreferences: AudioVaultAppPreferences = get()

    val downloadMethod = appPreferences.downloadMethod ?: suspendCancellableCoroutine { continuation ->
        AlertDialog.Builder(this)
            .setTitle(R.string.network_title)
            .setMessage(R.string.network_message)
            .setPositiveButton(R.string.wifi_only) { _, _ ->
                appPreferences.downloadMethod = DownloadMethod.WIFI_ONLY
                continuation.resume(DownloadMethod.WIFI_ONLY)
            }
            .setNegativeButton(R.string.mobile_data) { _, _ ->
                appPreferences.downloadMethod = DownloadMethod.MOBILE_DATA
                continuation.resume(DownloadMethod.MOBILE_DATA)
            }
            .setOnDismissListener {
                continuation.cancel(null)
            }
            .setCancelable(false)
            .show()
    }

    val permissionResult: Boolean = suspendCancellableCoroutine { continuation ->
        requestPermission("android.permission.POST_NOTIFICATIONS") { permissionsMap ->
            if (permissionsMap[Manifest.permission.POST_NOTIFICATIONS] == PackageManager.PERMISSION_GRANTED) {
                continuation.resume(true)
            } else {
                continuation.cancel(null)
            }
        }
    }

    if (!permissionResult) return

    get<DownloadManager>().applyDownloadMethod(downloadMethod)
    get<AudiobookDownloads>().download(trackIds)
}

suspend fun MainActivity.removeTracks(trackIds: List<String>) {
    val downloads: AudiobookDownloads = get()
    trackIds.forEach { downloads.remove(it) }
}

suspend fun MainActivity.removeDownload(download: LocalJellyfinMediaSource, force: Boolean = false) {
    if (!force) {
        val confirmation = suspendCancellableCoroutine { continuation ->
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.confirm_deletion))
                .setMessage(getString(R.string.confirm_deletion_desc, download.getName(this)))
                .setPositiveButton(getString(R.string.yes)) { _, _ ->
                    continuation.resume(true)
                }
                .setNegativeButton(getString(R.string.no)) { _, _ ->
                    continuation.cancel(null)
                }
                .setOnDismissListener {
                    continuation.cancel(null)
                }
                .setCancelable(false)
                .show()
        }

        if (!confirmation) return
    }

    get<AudiobookDownloads>().remove(download.itemId.toString())
}

val Context.isLowRamDevice: Boolean
    get() = getSystemService<ActivityManager>()!!.isLowRamDevice
