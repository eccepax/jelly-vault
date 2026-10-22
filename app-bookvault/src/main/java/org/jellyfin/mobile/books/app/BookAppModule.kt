package org.jellyfin.mobile.books.app

import coil3.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jellyfin.mobile.books.data.dao.BookBookmarkDao
import org.jellyfin.mobile.books.data.dao.BookProgressDao
import org.jellyfin.mobile.books.library.BookLibraryViewModel
import org.jellyfin.mobile.books.progress.BookProgressSync
import org.jellyfin.mobile.books.progress.JellyfinBookProgressServer
import org.jellyfin.mobile.books.reader.BookReaderViewModel
import org.jellyfin.mobile.common.app.AppPreferences
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val bookAppModule = module {
    single { BookAppPreferences(androidApplication()) }
    single<AppPreferences> { get<BookAppPreferences>() }
    single { ImageLoader(androidApplication()) }
    // Sends from a scope of its own, so leaving the reader doesn't cut a send off
    single {
        BookProgressSync(get(), JellyfinBookProgressServer(get()), CoroutineScope(SupervisorJob() + Dispatchers.IO))
    }

    // ViewModels
    viewModel { (libraryId: String?) ->
        BookLibraryViewModel(get(), get(), get<BookProgressDao>(), get(), libraryId, get<BookProgressSync>()::sync)
    }
    viewModel {
        BookReaderViewModel(
            androidApplication(),
            get<BookProgressSync>(),
            get<BookProgressDao>(),
            get(),
            get<BookBookmarkDao>(),
        )
    }
}
