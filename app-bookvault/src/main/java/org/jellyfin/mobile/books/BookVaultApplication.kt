package org.jellyfin.mobile.books

import android.app.Application
import org.jellyfin.mobile.books.app.bookAppModule
import org.jellyfin.mobile.books.data.bookDatabaseModule
import org.jellyfin.mobile.books.utils.BookTree
import org.jellyfin.mobile.common.app.apiModule
import org.jellyfin.sdk.model.ClientInfo
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.fragment.koin.fragmentFactory
import org.koin.core.context.startKoin
import timber.log.Timber

@Suppress("unused")
class BookVaultApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Timber.plant(BookTree())

        startKoin {
            androidContext(this@BookVaultApplication)
            fragmentFactory()

            modules(
                bookAppModule,
                apiModule(ClientInfo("BookVault", BuildConfig.VERSION_NAME)),
                bookDatabaseModule,
            )
        }
    }
}
