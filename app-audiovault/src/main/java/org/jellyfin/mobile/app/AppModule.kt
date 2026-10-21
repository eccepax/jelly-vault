package org.jellyfin.mobile.app

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil3.ImageLoader
import com.google.common.util.concurrent.Futures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.mobile.common.downloads.DownloadAwareDataSource
import org.jellyfin.mobile.common.downloads.ItemDownloads
import org.jellyfin.mobile.common.playback.SleepTimer
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.downloads.AudiobookDownloads
import org.jellyfin.mobile.downloads.DownloadsViewModel
import org.jellyfin.mobile.downloads.OfflineListeningSync
import org.jellyfin.mobile.downloads.audioItemIdOf
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.library.LibraryFragment
import org.jellyfin.mobile.library.LibraryViewModel
import org.jellyfin.mobile.player.AudiobookPlayerViewModel
import org.jellyfin.mobile.player.deviceprofile.DeviceProfileBuilder
import org.jellyfin.mobile.player.mediasegments.MediaSegmentRepository
import org.jellyfin.mobile.player.playback.AudiobookPlaybackController
import org.jellyfin.mobile.player.playback.AudiobookPlaybackService
import org.jellyfin.mobile.player.playback.AudiobookProgressReporter
import org.jellyfin.mobile.player.playback.AudiobookQueueLoader
import org.jellyfin.mobile.player.playback.AudiobookSessionCallback
import org.jellyfin.mobile.player.playback.AudiobookSleepTimerTarget
import org.jellyfin.mobile.player.playback.AudiobookVoiceSearch
import org.jellyfin.mobile.player.playback.DeferredAudiobookController
import org.jellyfin.mobile.player.playback.DeviceAudiobookProgress
import org.jellyfin.mobile.player.playback.JellyfinAudiobookProgress
import org.jellyfin.mobile.player.playback.JellyfinAudiobooks
import org.jellyfin.mobile.player.playback.MediaSegmentSkipper
import org.jellyfin.mobile.player.playback.PlayerAudiobookController
import org.jellyfin.mobile.player.playback.audioExtractorsFactory
import org.jellyfin.mobile.player.source.MediaSourceResolver
import org.jellyfin.mobile.player.ui.PlayerFragment
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.PermissionRequestHelper
import org.jellyfin.mobile.utils.isLowRamDevice
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.fragment.dsl.fragment
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val applicationModule = module {
    single { AudioVaultAppPreferences(androidApplication()) }
    single<AppPreferences> { get<AudioVaultAppPreferences>() }
    single { OkHttpClient() }
    single { ImageLoader(androidApplication()) }
    single { PermissionRequestHelper() }

    // Controllers

    // Event handlers and channels
    single { ActivityEventHandler() }

    // ViewModels
    viewModel { DownloadsViewModel(get<AudiobookDownloads>().finished) }
    viewModel {
        val downloads = get<AudiobookDownloads>()
        LibraryViewModel(
            get(),
            get(),
            get(),
            get(),
            downloads.downloadedIds,
            get(),
            downloads::playable,
            get<OfflineListeningSync>()::sync,
        )
    }
    viewModel { AudiobookPlayerViewModel(get(), get(), get(), get(), get()) }

    // Fragments
    fragment { LibraryFragment() }
    fragment { PlayerFragment() }

    // Connection helper

    // Playback: a MediaController bound to AudiobookPlaybackService, usable before it finishes connecting.
    single {
        val context = androidApplication()
        val audiobooks = get<JellyfinAudiobooks>()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val token = SessionToken(context, ComponentName(context, AudiobookPlaybackService::class.java))
        lateinit var deferred: DeferredAudiobookController
        // The service let go of the session (swiped away while paused): the next command reconnects
        val onDisconnected = object : MediaController.Listener {
            override fun onDisconnected(controller: MediaController) {
                controller.release()
                deferred.disconnected()
            }
        }
        val connect = {
            Futures.transform(
                MediaController.Builder(context, token).setListener(onDisconnected).buildAsync(),
                { controller ->
                    PlayerAudiobookController(controller, audiobooks::streamUrl) { enabled ->
                        val args = bundleOf(AudiobookSessionCallback.KEY_ENABLED to enabled)
                        controller.sendCustomCommand(AudiobookSessionCallback.SET_SKIP_SILENCE, args)
                    }
                },
                mainExecutor,
            )
        }
        deferred = DeferredAudiobookController(connect, mainExecutor, mainScope())
        deferred
    } bind AudiobookPlaybackController::class
    single { JellyfinAudiobooks(get()) }
    single {
        val audiobooks = get<JellyfinAudiobooks>()
        AudiobookQueueLoader(audiobooks::items, get<AudiobookDownloads>()::playable, audiobooks::coverUrl)
    }
    single { AudiobookVoiceSearch(get<JellyfinAudiobooks>()::topMatch, get()) }
    single {
        val downloadDao = get<DownloadDao>()
        val onDevice =
            DeviceAudiobookProgress(get<AudiobookDownloads>()::playable, { downloadDao.insert(DownloadEntity(it)) })
        AudiobookProgressReporter(get(), JellyfinAudiobookProgress(get()), onDevice, mainScope())
    }
    single { SleepTimer(AudiobookSleepTimerTarget(get()), mainScope()) }
    single {
        val repository = get<MediaSegmentRepository>()
        MediaSegmentSkipper(get(), repository::getSegmentsForItem, repository::getMediaSegmentAction, mainScope())
    }

    // Media player helpers
    single { MediaSourceResolver(get()) }
    single { DeviceProfileBuilder(Constants.APP_INFO_NAME, includeVideo = false) }
    single { MediaSegmentRepository() }

    // ExoPlayer factories
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

    // Plays a downloaded track from its file and streams everything else
    single {
        DownloadAwareDataSource.Factory(
            itemIdOf = ::audioItemIdOf,
            fileFor = get<ItemDownloads>()::fileFor,
            onDevice = FileDataSource.Factory(),
            server = get<DataSource.Factory>(),
        )
    }

    single<MediaSource.Factory> {
        val context: Context = get()
        DefaultMediaSourceFactory(
            get<DownloadAwareDataSource.Factory>(),
            audioExtractorsFactory(context.isLowRamDevice),
        )
    }
}

/** A scope for an app-wide piece of playback, on the main thread like the player it follows. */
private fun mainScope() = CoroutineScope(SupervisorJob() + Dispatchers.Main)
