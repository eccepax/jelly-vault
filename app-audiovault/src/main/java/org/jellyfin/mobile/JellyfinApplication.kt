package org.jellyfin.mobile

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import org.jellyfin.mobile.app.applicationModule
import org.jellyfin.mobile.common.app.apiModule
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.mobile.data.databaseModule
import org.jellyfin.mobile.downloads.audioDownloadsModule
import org.jellyfin.mobile.utils.JellyTree
import org.jellyfin.sdk.model.ClientInfo
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.fragment.koin.fragmentFactory
import org.koin.core.context.startKoin
import timber.log.Timber

/** The Koin modules the app starts with. */
@OptIn(UnstableApi::class)
val audioVaultModules = listOf(
    applicationModule,
    apiModule(ClientInfo("AudioVault", BuildConfig.VERSION_NAME), VaultBrand.AudioVault),
    databaseModule,
    audioDownloadsModule,
)

@Suppress("unused")
@OptIn(UnstableApi::class)
class JellyfinApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Setup logging
        Timber.plant(JellyTree())

        startKoin {
            androidContext(this@JellyfinApplication)
            fragmentFactory()

            modules(audioVaultModules)
        }
    }
}
