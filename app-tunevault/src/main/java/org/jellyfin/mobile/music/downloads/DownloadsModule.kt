package org.jellyfin.mobile.music.downloads

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.offline.DownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jellyfin.mobile.common.downloads.DownloadFiles
import org.jellyfin.mobile.common.downloads.DownloadGroups
import org.jellyfin.mobile.common.downloads.DownloadSettings
import org.jellyfin.mobile.common.downloads.ItemDownloads
import org.jellyfin.mobile.common.downloads.Media3DownloadSettings
import org.jellyfin.mobile.common.downloads.Media3ItemDownloads
import org.jellyfin.mobile.common.downloads.downloadRequirements
import org.jellyfin.mobile.common.downloads.fileDownloadManager
import org.jellyfin.mobile.common.server.withAccessToken
import org.jellyfin.mobile.music.app.PlaybackPreferences
import org.jellyfin.mobile.music.catalog.JellyfinMusicCatalog
import org.jellyfin.mobile.music.data.TuneDatabase
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import timber.log.Timber
import java.io.File

/** Where songs were kept before downloads moved to plain files; deleted, since nothing reads it now. */
private fun Context.deleteOldSongCache() {
    File(filesDir, "downloads").takeIf { it.isDirectory }?.deleteRecursively()
}

@UnstableApi
val downloadsModule = module {
    single<DatabaseProvider> { StandaloneDatabaseProvider(androidContext()) }
    single { DownloadFiles(File(androidContext().filesDir, "downloaded")) }
    single {
        val apiClient = get<ApiClient>()
        val authed = ResolvingDataSource.Factory(DefaultHttpDataSource.Factory()) { dataSpec ->
            dataSpec.withUri(Uri.parse(withAccessToken(dataSpec.uri.toString(), apiClient.accessToken)))
        }
        androidContext().deleteOldSongCache()
        fileDownloadManager(
            context = androidContext(),
            databaseProvider = get(),
            dataSourceFactory = authed,
            files = get(),
            requirements = downloadRequirements(get<PlaybackPreferences>().downloadOnWifiOnly),
        )
    }
    single<ItemDownloads> {
        val context = androidContext()
        Media3ItemDownloads(get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Main)) {
            try {
                DownloadService.start(context, TuneDownloadService::class.java)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Not allowed from the background; the manager still downloads while the app runs.
                Timber.w(e, "Couldn't start the download service")
            }
        }
    }
    single {
        val catalog = get<JellyfinMusicCatalog>()
        val preferences = get<PlaybackPreferences>()
        DownloadGroups(get(), get<TuneDatabase>().downloadGroupDao, uriOf = { trackId ->
            catalog.downloadUri(trackId, preferences.downloadQuality)
        })
    }
    single { DownloadRepository(get(), get<TuneDatabase>().downloadDao) }
    single<CollectionDownloads> { get<DownloadRepository>() }
    single<DownloadSettings> {
        val repository = get<DownloadRepository>()
        Media3DownloadSettings(get(), repository.bytesUsed, repository::removeAll)
    }
}
