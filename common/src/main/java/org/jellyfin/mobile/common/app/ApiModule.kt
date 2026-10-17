package org.jellyfin.mobile.common.app

import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.common.account.SignOut
import org.jellyfin.mobile.common.server.JellyfinServerSession
import org.jellyfin.mobile.common.server.ServerSession
import org.jellyfin.mobile.common.setup.ConnectionHelper
import org.jellyfin.mobile.common.setup.JellyfinPasswordSignIn
import org.jellyfin.mobile.common.setup.LoginViewModel
import org.jellyfin.mobile.common.setup.PasswordSignIn
import org.jellyfin.mobile.common.setup.quickconnect.JellyfinQuickConnect
import org.jellyfin.mobile.common.setup.quickconnect.QuickConnect
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

fun apiModule(clientInfo: ClientInfo, brand: VaultBrand) = module {
    // Also which app and version this is, e.g. for Settings' About
    single { clientInfo }
    // The app's look, for shared screens that can't know which app they're in
    single { brand }
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
    factory<QuickConnect> { JellyfinQuickConnect(get()) }
    factory<PasswordSignIn> { JellyfinPasswordSignIn(get()) }
    viewModel { LoginViewModel(get(), get(), get(), get()) }
    // Each app provides its SignOut (what it must finish and forget when signing out), or uses
    // plainSignOutModule when there's nothing beyond ending the session.
    viewModel { MainViewModel(get(), get(), get()) }
}

/** SignOut for an app with nothing of its own to finish or forget: end the session, log out. */
val plainSignOutModule = module {
    single { SignOut(get(), get<ApiClientController>()::logoutUser) }
}
