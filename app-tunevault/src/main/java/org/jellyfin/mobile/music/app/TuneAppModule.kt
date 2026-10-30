package org.jellyfin.mobile.music.app

import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.jellyfin.mobile.cast.Casting
import org.jellyfin.mobile.cast.casting
import org.jellyfin.mobile.common.account.DownloadsOnSignOut
import org.jellyfin.mobile.common.account.SignOut
import org.jellyfin.mobile.common.app.ApiClientController
import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.mobile.common.playback.SleepTimer
import org.jellyfin.mobile.music.account.SettingsViewModel
import org.jellyfin.mobile.music.account.tuneVaultSignOut
import org.jellyfin.mobile.music.catalog.AccountInfo
import org.jellyfin.mobile.music.catalog.FavoritesRepository
import org.jellyfin.mobile.music.catalog.FavoritesStore
import org.jellyfin.mobile.music.catalog.HomeFeed
import org.jellyfin.mobile.music.catalog.JellyfinAccountInfo
import org.jellyfin.mobile.music.catalog.JellyfinFavoritesStore
import org.jellyfin.mobile.music.catalog.JellyfinHomeFeed
import org.jellyfin.mobile.music.catalog.JellyfinMusicCatalog
import org.jellyfin.mobile.music.catalog.JellyfinMusicSearch
import org.jellyfin.mobile.music.catalog.JellyfinPlaylistStore
import org.jellyfin.mobile.music.catalog.MusicCatalog
import org.jellyfin.mobile.music.catalog.MusicSearch
import org.jellyfin.mobile.music.catalog.PlaylistStore
import org.jellyfin.mobile.music.data.TuneDatabase
import org.jellyfin.mobile.music.downloads.DownloadRepository
import org.jellyfin.mobile.music.downloads.DownloadedViewModel
import org.jellyfin.mobile.music.home.HomeViewModel
import org.jellyfin.mobile.music.library.AlbumDetailViewModel
import org.jellyfin.mobile.music.library.AlbumsViewModel
import org.jellyfin.mobile.music.library.ArtistDetailViewModel
import org.jellyfin.mobile.music.library.ArtistsViewModel
import org.jellyfin.mobile.music.library.SongsViewModel
import org.jellyfin.mobile.music.library.TrackActionsViewModel
import org.jellyfin.mobile.music.nowplaying.NowPlayingViewModel
import org.jellyfin.mobile.music.nowplaying.QueueViewModel
import org.jellyfin.mobile.music.offline.DownloadedLibrary
import org.jellyfin.mobile.music.offline.OfflineAwareFavoritesStore
import org.jellyfin.mobile.music.offline.OfflineAwareHomeFeed
import org.jellyfin.mobile.music.offline.OfflineAwareMusicCatalog
import org.jellyfin.mobile.music.offline.OfflineAwareMusicSearch
import org.jellyfin.mobile.music.offline.OfflineAwarePlaylistStore
import org.jellyfin.mobile.music.offline.ServerConnectivity
import org.jellyfin.mobile.music.offline.reloadingOnModeChange
import org.jellyfin.mobile.music.playback.DeferredPlaybackController
import org.jellyfin.mobile.music.playback.JellyfinPlaybackReporter
import org.jellyfin.mobile.music.playback.OfflinePlayReporter
import org.jellyfin.mobile.music.playback.PlaybackController
import org.jellyfin.mobile.music.playback.PlaybackReporter
import org.jellyfin.mobile.music.playback.PlaybackReportingTracker
import org.jellyfin.mobile.music.playback.PlaybackService
import org.jellyfin.mobile.music.playback.PlaybackSleepTimerTarget
import org.jellyfin.mobile.music.playback.PlaybackVolume
import org.jellyfin.mobile.music.playback.PlayerPlaybackController
import org.jellyfin.mobile.music.playback.SleepTimerVisibility
import org.jellyfin.mobile.music.playback.VolumeLeveller
import org.jellyfin.mobile.music.playback.VolumeLevellingSetting
import org.jellyfin.mobile.music.playlists.AddToPlaylistViewModel
import org.jellyfin.mobile.music.playlists.PlaylistDetailViewModel
import org.jellyfin.mobile.music.playlists.PlaylistsViewModel
import org.jellyfin.mobile.music.search.SearchViewModel
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.core.scope.Scope
import org.koin.dsl.module

private const val PING_TIMEOUT_MS = 3_000L

/** Whether the app is showing the downloads because the Vault can't be reached. */
private fun Scope.isOffline() = get<ServerConnectivity>().isOffline
private const val SYNC_TIMEOUT_MS = 5_000L

val tuneAppModule = module {
    single { TuneAppPreferences(androidApplication()) }
    single<AppPreferences> { get<TuneAppPreferences>() }
    single<LibraryPreferences> { get<TuneAppPreferences>() }
    single<PlaybackPreferences> { get<TuneAppPreferences>() }
    single { OkHttpClient() }

    // Controllers

    // Catalog
    single {
        val preferences = get<PlaybackPreferences>()
        JellyfinMusicCatalog(get(), streamingQuality = { preferences.streamingQuality })
    }
    single<AccountInfo> { JellyfinAccountInfo(get()) }

    // Offline: reads fall back to the downloads while the Vault can't be reached.
    single {
        val apiClient = get<ApiClient>()
        ServerConnectivity(
            ping = { withTimeoutOrNull(PING_TIMEOUT_MS) { apiClient.systemApi.getPingSystem() } != null },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
        )
    }
    single {
        val downloads = get<DownloadRepository>()
        DownloadedLibrary(downloads.collections, downloads.downloadedTrackIds)
    }
    single<MusicCatalog> { OfflineAwareMusicCatalog(get<JellyfinMusicCatalog>(), get(), get()) }
    single<MusicSearch> { OfflineAwareMusicSearch(JellyfinMusicSearch(get()), get(), get()) }
    single<HomeFeed> { OfflineAwareHomeFeed(JellyfinHomeFeed(get()), get(), get()) }
    single<PlaylistStore> { OfflineAwarePlaylistStore(JellyfinPlaylistStore(get()), get(), get()) }
    single<FavoritesStore> { OfflineAwareFavoritesStore(JellyfinFavoritesStore(get()), get()) }
    single { FavoritesRepository(get(), CoroutineScope(SupervisorJob() + Dispatchers.Main)) }

    // Playback: a MediaController bound to PlaybackService, usable before it finishes connecting.
    single<PlaybackController> {
        val context = androidApplication()
        val catalog = get<MusicCatalog>()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val connection = Futures.transform(
            MediaController.Builder(context, token).buildAsync(),
            { mediaController -> PlayerPlaybackController(mediaController, catalog::streamUrl) },
            mainExecutor,
        )
        DeferredPlaybackController(connection, mainExecutor, CoroutineScope(SupervisorJob() + Dispatchers.Main))
    }
    // Chromecast where Google Play services can run it (the service moves its session onto it)
    single<Casting> { casting(androidApplication()) }
    single { OfflinePlayReporter(JellyfinPlaybackReporter(get()), get<TuneDatabase>().pendingPlayDao) }
    single<PlaybackReporter> { get<OfflinePlayReporter>() }
    single { PlaybackReportingTracker(get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Main)) }
    single { PlaybackVolume(get()) }
    single { SleepTimer(PlaybackSleepTimerTarget(get(), get()), CoroutineScope(SupervisorJob() + Dispatchers.Main)) }
    single { VolumeLevellingSetting(get()) }
    single {
        VolumeLeveller(
            get(),
            get(),
            get<VolumeLevellingSetting>().isOn,
            CoroutineScope(SupervisorJob() + Dispatchers.Main),
        )
    }
    single { SleepTimerVisibility(get(), get()) }

    // Account
    single {
        val apiClientController = get<ApiClientController>()
        val downloads = get<DownloadRepository>()
        val offlinePlays = get<OfflinePlayReporter>()
        tuneVaultSignOut(
            get(),
            get(),
            get(),
            get(),
            clearAccountData = {
                withTimeoutOrNull(SYNC_TIMEOUT_MS) { offlinePlays.sync() }
                offlinePlays.clear()
                DownloadsOnSignOut(get(), downloads::removeAll)()
            },
            logout = apiClientController::logoutUser,
            forgetCast = get<Casting>()::forgetCast,
        )
    }

    // ViewModels
    viewModel { AlbumsViewModel(get(), get()).reloadingOnModeChange(isOffline(), AlbumsViewModel::retry) }
    viewModel { (albumId: String) ->
        AlbumDetailViewModel(
            albumId,
            get(),
            get(),
            get(),
            get(),
        ).reloadingOnModeChange(isOffline(), AlbumDetailViewModel::retry)
    }
    viewModel { NowPlayingViewModel(get(), get()) }
    viewModel { ArtistsViewModel(get()).reloadingOnModeChange(isOffline(), ArtistsViewModel::retry) }
    viewModel { (artistId: String) ->
        ArtistDetailViewModel(artistId, get()).reloadingOnModeChange(isOffline(), ArtistDetailViewModel::retry)
    }
    viewModel { SongsViewModel(get(), get()).reloadingOnModeChange(isOffline(), SongsViewModel::retry) }
    viewModel { SearchViewModel(get(), get(), get()) }
    viewModel { TrackActionsViewModel(get(), get()) }
    viewModel { QueueViewModel(get()) }
    viewModel { PlaylistsViewModel(get()).reloadingOnModeChange(isOffline(), PlaylistsViewModel::refresh) }
    viewModel { (playlistId: String) ->
        PlaylistDetailViewModel(playlistId, get(), get(), get())
            .reloadingOnModeChange(isOffline(), PlaylistDetailViewModel::refresh)
    }
    viewModel { AddToPlaylistViewModel(get()) }
    viewModel { DownloadedViewModel(get(), get()) }
    viewModel { HomeViewModel(get(), get(), get(), get()).reloadingOnModeChange(isOffline(), HomeViewModel::refresh) }
    viewModel {
        val signOut = get<SignOut>()
        SettingsViewModel(
            get(),
            get(),
            get(),
            sleepTimer = get<SleepTimerVisibility>(),
            levelling = get<VolumeLevellingSetting>(),
            signOut = { signOut() },
            app = get(),
        )
    }
}
