package org.jellyfin.mobile

import android.app.Application
import org.jellyfin.mobile.app.applicationModule
import org.jellyfin.mobile.common.app.apiModule
import org.jellyfin.mobile.data.databaseModule
import org.jellyfin.mobile.utils.JellyTree
import org.jellyfin.sdk.model.ClientInfo
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.fragment.koin.fragmentFactory
import org.koin.core.context.startKoin
import timber.log.Timber

@Suppress("unused")
class JellyfinApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Setup logging
        Timber.plant(JellyTree())

        startKoin {
            androidContext(this@JellyfinApplication)
            fragmentFactory()

            modules(
                applicationModule,
                apiModule(ClientInfo("AudioVault", BuildConfig.VERSION_NAME)),
                databaseModule,
            )
        }
    }
}
