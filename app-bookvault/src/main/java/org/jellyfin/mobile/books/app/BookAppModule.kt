package org.jellyfin.mobile.books.app

import coil3.ImageLoader
import okhttp3.OkHttpClient
import org.jellyfin.mobile.books.data.dao.BookBookmarkDao
import org.jellyfin.mobile.books.data.dao.BookProgressDao
import org.jellyfin.mobile.books.downloads.BookDownloadManager
import org.jellyfin.mobile.books.library.BookLibraryViewModel
import org.jellyfin.mobile.books.reader.BookReaderViewModel
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.common.account.SignOut
import org.jellyfin.mobile.common.app.ApiClientController
import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.mobile.common.data.dao.DownloadRecordDao
import org.jellyfin.mobile.common.setup.ConnectionHelper
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val bookAppModule = module {
    single { BookAppPreferences(androidApplication()) }
    single<AppPreferences> { get<BookAppPreferences>() }
    single { OkHttpClient() }
    single { ImageLoader(androidApplication()) }

    // Controllers
    single { ApiClientController(get(), get(), get(), get(), get()) }
    single { ConnectionHelper(get(), get()) }

    // Download manager
    single { BookDownloadManager(androidApplication(), get(), get(), get<DownloadRecordDao>()) }

    // ViewModels
    single { SignOut(get(), get<ApiClientController>()::logoutUser) }
    viewModel { MainViewModel(get(), get(), get()) }
    viewModel { (libraryId: String?) ->
        BookLibraryViewModel(get(), get(), get<BookProgressDao>(), get(), get<DownloadRecordDao>(), libraryId)
    }
    viewModel {
        BookReaderViewModel(androidApplication(), get(), get(), get<BookProgressDao>(), get(), get<BookBookmarkDao>())
    }
}
