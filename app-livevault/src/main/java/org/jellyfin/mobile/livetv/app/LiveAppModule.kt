package org.jellyfin.mobile.livetv.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.mobile.livetv.catalog.JellyfinLiveTvCatalog
import org.jellyfin.mobile.livetv.catalog.LiveTvCatalog
import org.jellyfin.mobile.livetv.channels.ChannelsViewModel
import org.jellyfin.mobile.livetv.playback.ExoLivePlayer
import org.jellyfin.mobile.livetv.playback.JellyfinLiveStreams
import org.jellyfin.mobile.livetv.playback.JellyfinLiveTvSessions
import org.jellyfin.mobile.livetv.playback.LiveStreams
import org.jellyfin.mobile.livetv.playback.LiveTvSessions
import org.jellyfin.mobile.livetv.playback.PreferencesStreamLedger
import org.jellyfin.mobile.livetv.playback.StreamCloser
import org.jellyfin.mobile.livetv.playback.StreamLedger
import org.jellyfin.mobile.livetv.playback.WatchViewModel
import org.jellyfin.mobile.player.deviceprofile.DeviceProfileBuilder
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val liveAppModule = module {
    single { AppPreferences(androidApplication()) }

    single<LiveTvCatalog> { JellyfinLiveTvCatalog(get()) }
    viewModel { ChannelsViewModel(get(), get()) }

    // Playback
    single { DeviceProfileBuilder("LiveVault", includeVideo = true) }
    single<LiveStreams> { JellyfinLiveStreams(get(), get<DeviceProfileBuilder>()::getDeviceProfile) }
    single<StreamLedger> { PreferencesStreamLedger(androidContext()) }
    // App-wide scope: a stream is closed even when the screen that opened it is gone.
    single { StreamCloser(get(), get(), scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    single<LiveTvSessions> { JellyfinLiveTvSessions(get()) }
    viewModel { WatchViewModel(get(), get(), ExoLivePlayer(androidContext()), get()) }
}
