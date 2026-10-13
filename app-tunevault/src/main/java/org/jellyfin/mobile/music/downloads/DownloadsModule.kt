package org.jellyfin.mobile.music.downloads

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jellyfin.mobile.music.app.PlaybackPreferences
import org.jellyfin.mobile.music.catalog.JellyfinMusicCatalog
import org.jellyfin.mobile.music.data.TuneDatabase
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import timber.log.Timber
import java.io.File
import java.util.concurrent.Executors

private const val DOWNLOAD_THREADS = 2

/** Wi-Fi only means any unmetered network; otherwise any network at all. */
fun downloadRequirements(wifiOnly: Boolean) =
    Requirements(if (wifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)

@UnstableApi
val downloadsModule = module {
    single<DatabaseProvider> { StandaloneDatabaseProvider(androidContext()) }
    // One per process: SimpleCache locks its folder. Never evicts, since downloads are kept on purpose.
    single<Cache> { SimpleCache(File(androidContext().filesDir, "downloads"), NoOpCacheEvictor(), get()) }
    single {
        val apiClient = get<ApiClient>()
        val authed = ResolvingDataSource.Factory(DefaultHttpDataSource.Factory()) { dataSpec ->
            dataSpec.withUri(Uri.parse(withAccessToken(dataSpec.uri.toString(), apiClient.accessToken)))
        }
        val writer = CacheDataSource.Factory()
            .setCache(get())
            .setCacheKeyFactory(TrackCacheKey.factory)
            .setUpstreamDataSourceFactory(authed)
        DownloadManager(
            androidContext(),
            DefaultDownloadIndex(get()),
            DefaultDownloaderFactory(writer, Executors.newFixedThreadPool(DOWNLOAD_THREADS)),
        ).apply {
            maxParallelDownloads = DOWNLOAD_THREADS
            requirements = downloadRequirements(get<PlaybackPreferences>().downloadOnWifiOnly)
        }
    }
    single<TrackDownloader> {
        val context = androidContext()
        Media3TrackDownloader(get(), CoroutineScope(SupervisorJob() + Dispatchers.Main)) {
            try {
                DownloadService.start(context, TuneDownloadService::class.java)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Not allowed from the background; the manager still downloads while the app runs.
                Timber.w(e, "Couldn't start the download service")
            }
        }
    }
    single<CollectionDownloads> { get<DownloadRepository>() }
    single<DownloadSettings> { Media3DownloadSettings(get(), get()) }
    single {
        val catalog = get<JellyfinMusicCatalog>()
        val preferences = get<PlaybackPreferences>()
        DownloadRepository(get(), get<TuneDatabase>().downloadDao, downloadUri = { trackId ->
            catalog.downloadUri(trackId, preferences.downloadQuality)
        })
    }
}
