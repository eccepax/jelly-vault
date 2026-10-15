package org.jellyfin.mobile.books

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import org.jellyfin.mobile.books.app.bookAppModule
import org.jellyfin.mobile.books.data.bookDatabaseModule
import org.jellyfin.mobile.books.downloads.bookDownloadsModule
import org.jellyfin.mobile.books.utils.BookTree
import org.jellyfin.mobile.common.app.apiModule
import org.jellyfin.sdk.model.ClientInfo
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.fragment.koin.fragmentFactory
import org.koin.core.context.startKoin
import timber.log.Timber

/** The Koin modules the app starts with. */
@OptIn(UnstableApi::class)
val bookVaultModules = listOf(
    bookAppModule,
    apiModule(ClientInfo("BookVault", BuildConfig.VERSION_NAME)),
    bookDatabaseModule,
    bookDownloadsModule,
)

@Suppress("unused")
@OptIn(UnstableApi::class)
class BookVaultApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Timber.plant(BookTree())

        startKoin {
            androidContext(this@BookVaultApplication)
            fragmentFactory()

            modules(bookVaultModules)
        }
    }
}
