package org.jellyfin.mobile.common.app

import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.common.server.JellyfinServerSession
import org.jellyfin.mobile.common.server.ServerSession
import org.jellyfin.mobile.common.setup.ConnectionHelper
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

fun apiModule(clientInfo: ClientInfo) = module {
    single {
        createJellyfin {
            context = androidContext()
            this.clientInfo = clientInfo
        }
    }
    single { get<Jellyfin>().createApi() }
    single<ServerSession> { JellyfinServerSession(get()) }
    single { ApiClientController(get(), get(), get(), get(), get()) }
    single { ConnectionHelper(get(), get()) }
    // Each app provides its SignOut (what it must finish and forget when signing out).
    viewModel { MainViewModel(get(), get(), get()) }
}
