package org.jellyfin.mobile.music

import android.app.Application
import org.jellyfin.mobile.common.app.apiModule
import org.jellyfin.mobile.music.app.tuneAppModule
import org.jellyfin.mobile.music.data.tuneDatabaseModule
import org.jellyfin.mobile.music.downloads.downloadsModule
import org.jellyfin.mobile.music.playback.PlaybackReportingTracker
import org.jellyfin.mobile.music.playback.VolumeLeveller
import org.jellyfin.sdk.model.ClientInfo
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber

@Suppress("unused")
class TuneVaultApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())

        val koin = startKoin {
            androidContext(this@TuneVaultApplication)

            modules(
                tuneAppModule,
                apiModule(ClientInfo("TuneVault", BuildConfig.VERSION_NAME)),
                tuneDatabaseModule,
                downloadsModule,
            )
        }.koin

        // Reports what's played to the server for the life of the process (which the playback
        // service keeps alive while music plays), independent of any screen.
        koin.get<PlaybackReportingTracker>().start()
        koin.get<VolumeLeveller>().start()
    }
}
