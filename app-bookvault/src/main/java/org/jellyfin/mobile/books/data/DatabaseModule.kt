package org.jellyfin.mobile.books.data

import androidx.room.Room
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

val bookDatabaseModule = module {
    single {
        Room.databaseBuilder(androidApplication(), BookDatabase::class.java, "bookvault")
            .fallbackToDestructiveMigrationFrom(true, 0, 1, 2)
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .build()
    }
    single { get<BookDatabase>().bookProgressDao }
    single { get<BookDatabase>().serverDao }
    single { get<BookDatabase>().userDao }
    single { get<BookDatabase>().downloadGroupDao }
    single { get<BookDatabase>().bookBookmarkDao }
}
