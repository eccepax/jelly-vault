package org.jellyfin.mobile.livetv.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import org.jellyfin.mobile.common.data.dao.ServerDao
import org.jellyfin.mobile.common.data.dao.UserDao
import org.jellyfin.mobile.common.data.entity.ServerEntity
import org.jellyfin.mobile.common.data.entity.UserEntity
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

@Database(entities = [ServerEntity::class, UserEntity::class], version = 1)
abstract class LiveDatabase : RoomDatabase() {
    abstract val serverDao: ServerDao
    abstract val userDao: UserDao
}

val liveDatabaseModule = module {
    single {
        Room.databaseBuilder(androidApplication(), LiveDatabase::class.java, "livevault")
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .build()
    }
    single { get<LiveDatabase>().serverDao }
    single { get<LiveDatabase>().userDao }
}
