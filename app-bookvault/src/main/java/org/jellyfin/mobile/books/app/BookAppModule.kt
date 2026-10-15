package org.jellyfin.mobile.books.app

import coil3.ImageLoader
import org.jellyfin.mobile.books.data.dao.BookBookmarkDao
import org.jellyfin.mobile.books.data.dao.BookProgressDao
import org.jellyfin.mobile.books.library.BookLibraryViewModel
import org.jellyfin.mobile.books.reader.BookReaderViewModel
import org.jellyfin.mobile.common.app.AppPreferences
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val bookAppModule = module {
    single { BookAppPreferences(androidApplication()) }
    single<AppPreferences> { get<BookAppPreferences>() }
    single { ImageLoader(androidApplication()) }

    // ViewModels
    viewModel { (libraryId: String?) ->
        BookLibraryViewModel(get(), get(), get<BookProgressDao>(), get(), libraryId)
    }
    viewModel {
        BookReaderViewModel(androidApplication(), get(), get<BookProgressDao>(), get(), get<BookBookmarkDao>())
    }
}
