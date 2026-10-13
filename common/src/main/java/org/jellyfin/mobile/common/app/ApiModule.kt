package org.jellyfin.mobile.common.app

import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

fun apiModule(clientInfo: ClientInfo) = module {
    single {
        createJellyfin {
            context = androidContext()
            this.clientInfo = clientInfo
        }
    }
    single { get<Jellyfin>().createApi() }
}
