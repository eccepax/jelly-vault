package org.jellyfin.mobile.music.data

import androidx.room.Room
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

val tuneDatabaseModule = module {
    single {
        Room.databaseBuilder(androidApplication(), TuneDatabase::class.java, "tunevault")
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .build()
    }
    single { get<TuneDatabase>().serverDao }
    single { get<TuneDatabase>().userDao }
}
