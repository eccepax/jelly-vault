package org.jellyfin.mobile.livetv

import android.app.Application
import org.jellyfin.mobile.common.app.apiModule
import org.jellyfin.mobile.common.app.plainSignOutModule
import org.jellyfin.mobile.livetv.app.liveAppModule
import org.jellyfin.mobile.livetv.data.liveDatabaseModule
import org.jellyfin.sdk.model.ClientInfo
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber

@Suppress("unused")
class LiveVaultApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())

        startKoin {
            androidContext(this@LiveVaultApplication)
            modules(
                liveAppModule,
                apiModule(ClientInfo("LiveVault", BuildConfig.VERSION_NAME)),
                liveDatabaseModule,
                plainSignOutModule,
            )
        }
    }
}
