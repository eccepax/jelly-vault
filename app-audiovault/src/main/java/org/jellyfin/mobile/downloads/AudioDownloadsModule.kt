package org.jellyfin.mobile.downloads

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.common.account.DownloadsOnSignOut
import org.jellyfin.mobile.common.account.SignOut
import org.jellyfin.mobile.common.app.ApiClientController
import org.jellyfin.mobile.common.downloads.DownloadFiles
import org.jellyfin.mobile.common.downloads.DownloadGroups
import org.jellyfin.mobile.common.downloads.ItemDownloads
import org.jellyfin.mobile.common.downloads.Media3ItemDownloads
import org.jellyfin.mobile.common.downloads.downloadRequirements
import org.jellyfin.mobile.common.downloads.fileDownloadManager
import org.jellyfin.mobile.common.server.itemDownloadUrl
import org.jellyfin.mobile.data.JellyfinDatabase
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.player.deviceprofile.DeviceProfileBuilder
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.jellyfin.mobile.player.source.MediaSourceResolver
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.serializer.toUUID
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import timber.log.Timber
import java.io.File

/** Wi-Fi only waits for an unmetered network; mobile data (with or without roaming) takes any network. */
@UnstableApi
fun DownloadManager.applyDownloadMethod(@DownloadMethod method: Int?) {
    requirements = downloadRequirements(wifiOnly = method == DownloadMethod.WIFI_ONLY)
}

/** Where a track's file is fetched from; the data source adds the authorization. */
private fun ApiClient.trackUri(trackId: String): String =
    itemDownloadUrl(requireNotNull(baseUrl) { "No server" }, trackId.toUUID(), token = null)

/** Where tracks were kept before downloads moved to the shared engine; nothing reads it now. */
private fun Context.deleteOldTrackCache() {
    File(filesDir, "MediaCache").takeIf { it.isDirectory }?.deleteRecursively()
    File(filesDir, "Downloads").takeIf { it.isDirectory }?.deleteRecursively()
}

@UnstableApi
val audioDownloadsModule = module {
    single<DatabaseProvider> { StandaloneDatabaseProvider(androidContext()) }
    single { DownloadFiles(File(androidContext().filesDir, "downloaded")) }
    single {
        androidContext().deleteOldTrackCache()
        fileDownloadManager(androidContext(), get(), get<DataSource.Factory>(), get()).apply {
            applyDownloadMethod(get<AudioVaultAppPreferences>().downloadMethod)
        }
    }
    single<ItemDownloads> {
        val context = androidContext()
        Media3ItemDownloads(get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Main)) {
            try {
                DownloadService.start(context, AudioDownloadService::class.java)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Not allowed from the background; the manager still downloads while the app runs.
                Timber.w(e, "Couldn't start the download service")
            }
        }
    }
    single {
        val apiClient = get<ApiClient>()
        DownloadGroups(get(), get<JellyfinDatabase>().downloadGroupDao, uriOf = apiClient::trackUri)
    }
    single {
        val apiClient = get<ApiClient>()
        val resolver = get<MediaSourceResolver>()
        val profile = get<DeviceProfileBuilder>().getDeviceProfile()
        val files = get<DownloadFiles>()
        AudiobookDownloads(get(), get<JellyfinDatabase>().downloadDao) { trackId ->
            val id = trackId.toUUID()
            resolver.resolveMediaSource(itemId = id, mediaSourceId = trackId.replace("-", ""), deviceProfile = profile)
                .onFailure { Timber.w(it, "Couldn't load the details of %s", trackId) }
                .getOrNull()
                ?.let { source ->
                    DownloadEntity(LocalJellyfinMediaSource(source, files.root.path, apiClient.trackUri(trackId), 0))
                }
        }
    }
    // Signing out keeps downloads unless the user asked otherwise in Settings
    single {
        val downloadsOnSignOut = DownloadsOnSignOut(get(), get<AudiobookDownloads>()::removeAll)
        SignOut(get(), get<ApiClientController>()::logoutUser, forgetAccount = { downloadsOnSignOut() })
    }
}
