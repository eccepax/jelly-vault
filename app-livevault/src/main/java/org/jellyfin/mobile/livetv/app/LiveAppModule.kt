package org.jellyfin.mobile.livetv.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jellyfin.mobile.cast.Casting
import org.jellyfin.mobile.cast.NoCasting
import org.jellyfin.mobile.cast.casting
import org.jellyfin.mobile.cast.liveCasting
import org.jellyfin.mobile.common.account.SignOut
import org.jellyfin.mobile.common.app.ApiClientController
import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.mobile.livetv.account.AccountViewModel
import org.jellyfin.mobile.livetv.account.liveVaultSignOut
import org.jellyfin.mobile.livetv.catalog.JellyfinLiveTvCatalog
import org.jellyfin.mobile.livetv.catalog.LiveTvCatalog
import org.jellyfin.mobile.livetv.channels.ChannelsViewModel
import org.jellyfin.mobile.livetv.playback.ExoLivePlayer
import org.jellyfin.mobile.livetv.playback.JellyfinLiveStreams
import org.jellyfin.mobile.livetv.playback.JellyfinLiveTvSessions
import org.jellyfin.mobile.livetv.playback.LiveStreams
import org.jellyfin.mobile.livetv.playback.LiveTvSessions
import org.jellyfin.mobile.livetv.playback.PreferencesStreamLedger
import org.jellyfin.mobile.livetv.playback.ServiceCastKeepAlive
import org.jellyfin.mobile.livetv.playback.StreamCloser
import org.jellyfin.mobile.livetv.playback.StreamLedger
import org.jellyfin.mobile.livetv.playback.WatchViewModel
import org.jellyfin.mobile.livetv.viewing.PreferencesViewingMemory
import org.jellyfin.mobile.livetv.viewing.ViewingMemory
import org.jellyfin.mobile.player.deviceprofile.DeviceProfileBuilder
import org.jellyfin.mobile.player.deviceprofile.castVideoProfile
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val liveAppModule = module {
    single { AppPreferences(androidApplication()) }

    single<LiveTvCatalog> { JellyfinLiveTvCatalog(get()) }
    single<ViewingMemory> { PreferencesViewingMemory(androidContext()) }
    viewModel { ChannelsViewModel(get(), get(), get()) }

    // Playback
    single { DeviceProfileBuilder("LiveVault", includeVideo = true) }
    single<LiveStreams> {
        JellyfinLiveStreams(
            get(),
            get<DeviceProfileBuilder>()::getDeviceProfile,
            castProfile = { castVideoProfile("LiveVault") },
        )
    }
    // None on a TV: it's where things are cast to, not from
    single<Casting> { if (androidContext().isTelevision) NoCasting else casting(androidApplication()) }
    single<StreamLedger> { PreferencesStreamLedger(androidContext()) }
    // App-wide scope: a stream is closed even when the screen that opened it is gone.
    single { StreamCloser(get(), get(), scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    single<LiveTvSessions> { JellyfinLiveTvSessions(get()) }
    // Account
    single {
        liveVaultSignOut(
            get(),
            get(),
            get(),
            get<ApiClientController>()::logoutUser,
            forgetCast = get<Casting>()::forgetCast,
        )
    }
    viewModel { AccountViewModel(signOut = { get<SignOut>()() }) }

    viewModel {
        WatchViewModel(
            get(),
            get(),
            ExoLivePlayer(androidContext()),
            liveCasting(get()),
            get(),
            get(),
            keepAlive = ServiceCastKeepAlive(androidContext()),
        )
    }
}
