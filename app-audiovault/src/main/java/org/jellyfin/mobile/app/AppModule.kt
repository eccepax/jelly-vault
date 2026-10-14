package org.jellyfin.mobile.app

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.util.Util
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.TsExtractor
import coil3.ImageLoader
import kotlinx.coroutines.channels.Channel
import okhttp3.OkHttpClient
import org.jellyfin.mobile.common.account.SignOut
import org.jellyfin.mobile.common.app.ApiClientController
import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.mobile.downloads.DownloadsViewModel
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.library.LibraryFragment
import org.jellyfin.mobile.library.LibraryViewModel
import org.jellyfin.mobile.player.AudiobookPlayerViewModel
import org.jellyfin.mobile.player.deviceprofile.DeviceProfileBuilder
import org.jellyfin.mobile.player.interaction.PlayerEvent
import org.jellyfin.mobile.player.mediasegments.MediaSegmentRepository
import org.jellyfin.mobile.player.source.MediaSourceResolver
import org.jellyfin.mobile.player.ui.PlayerFragment
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.PermissionRequestHelper
import org.jellyfin.mobile.utils.extractId
import org.jellyfin.mobile.utils.isLowRamDevice
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.fragment.dsl.fragment
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File

const val PLAYER_EVENT_CHANNEL = "PlayerEventChannel"
private const val TS_SEARCH_PACKETS = 1800

val applicationModule = module {
    single { AudioVaultAppPreferences(androidApplication()) }
    single<AppPreferences> { get<AudioVaultAppPreferences>() }
    single { OkHttpClient() }
    single { ImageLoader(androidApplication()) }
    single { PermissionRequestHelper() }
    single(named(PLAYER_EVENT_CHANNEL)) { Channel<PlayerEvent>() }

    // Controllers

    // Event handlers and channels
    single { ActivityEventHandler() }

    // ViewModels
    single { SignOut(get(), get<ApiClientController>()::logoutUser) }
    viewModel { DownloadsViewModel() }
    viewModel { LibraryViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { AudiobookPlayerViewModel(get()) }

    // Fragments
    fragment { LibraryFragment() }
    fragment { PlayerFragment() }

    // Connection helper

    // Media player helpers
    single { MediaSourceResolver(get()) }
    single { DeviceProfileBuilder() }
    single { MediaSegmentRepository() }

    // ExoPlayer factories
    single<DatabaseProvider> {
        val dbProvider = StandaloneDatabaseProvider(get<Context>())
        dbProvider
    }
    single<Cache> {
        val downloadPath = File(get<Context>().filesDir, Constants.DOWNLOAD_PATH)
        if (!downloadPath.exists()) {
            downloadPath.mkdirs()
        }
        val cache = SimpleCache(downloadPath, NoOpCacheEvictor(), get())
        cache
    }

    single<DataSource.Factory> {
        val context: Context = get()
        val apiClient: ApiClient = get()

        val baseDataSourceFactory = DefaultHttpDataSource.Factory().apply {
            setUserAgent(Util.getUserAgent(context, Constants.APP_INFO_NAME))
        }

        val dataSourceFactory = DefaultDataSource.Factory(context, baseDataSourceFactory)

        // Add authorization header. This is needed as we don't pass the
        // access token in the URL for Android Auto.
        ResolvingDataSource.Factory(dataSourceFactory) { dataSpec: DataSpec ->
            // Only send authorization header if URI matches the jellyfin server
            val baseUrlAuthority = apiClient.baseUrl?.toUri()?.authority

            if (dataSpec.uri.authority == baseUrlAuthority) {
                val authorizationHeaderString = AuthorizationHeaderBuilder.buildHeader(
                    clientName = apiClient.clientInfo.name,
                    clientVersion = apiClient.clientInfo.version,
                    deviceId = apiClient.deviceInfo.id,
                    deviceName = apiClient.deviceInfo.name,
                    accessToken = apiClient.accessToken,
                )

                dataSpec.withRequestHeaders(hashMapOf("Authorization" to authorizationHeaderString))
            } else {
                dataSpec
            }
        }
    }

    single<CacheDataSource.Factory> {
        // Create a read-only cache data source factory using the download cache.
        CacheDataSource.Factory()
            .setCache(get())
            .setUpstreamDataSourceFactory(get<DataSource.Factory>())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .setCacheWriteDataSinkFactory(null)
            .setCacheKeyFactory { spec ->
                spec.uri.extractId()
            }
    }

    single<MediaSource.Factory> {
        val context: Context = get()
        val extractorsFactory = DefaultExtractorsFactory().apply {
            // https://github.com/google/ExoPlayer/issues/8571
            setTsExtractorTimestampSearchBytes(
                when {
                    !context.isLowRamDevice -> TS_SEARCH_PACKETS * TsExtractor.TS_PACKET_SIZE // 3x default
                    else -> TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES
                },
            )
        }
        DefaultMediaSourceFactory(get<CacheDataSource.Factory>(), extractorsFactory)
    }
    single { ProgressiveMediaSource.Factory(get<CacheDataSource.Factory>()) }
    single { HlsMediaSource.Factory(get<CacheDataSource.Factory>()) }
    single { SingleSampleMediaSource.Factory(get<CacheDataSource.Factory>()) }
}
