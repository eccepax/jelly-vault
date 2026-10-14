package org.jellyfin.mobile.livetv.app

import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.mobile.livetv.catalog.JellyfinLiveTvCatalog
import org.jellyfin.mobile.livetv.catalog.LiveTvCatalog
import org.jellyfin.mobile.livetv.channels.ChannelsViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val liveAppModule = module {
    single { AppPreferences(androidApplication()) }

    single<LiveTvCatalog> { JellyfinLiveTvCatalog(get()) }
    viewModel { ChannelsViewModel(get(), get()) }
}
