package org.jellyfin.mobile.books.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import org.jellyfin.mobile.books.data.dao.BookBookmarkDao
import org.jellyfin.mobile.books.data.dao.BookProgressDao
import org.jellyfin.mobile.books.data.entity.BookBookmarkEntity
import org.jellyfin.mobile.books.data.entity.BookProgressEntity
import org.jellyfin.mobile.common.data.dao.DownloadRecordDao
import org.jellyfin.mobile.common.data.dao.ServerDao
import org.jellyfin.mobile.common.data.dao.UserDao
import org.jellyfin.mobile.common.data.entity.DownloadRecord
import org.jellyfin.mobile.common.data.entity.ServerEntity
import org.jellyfin.mobile.common.data.entity.UserEntity

@Database(
    entities = [
        BookProgressEntity::class,
        ServerEntity::class,
        UserEntity::class,
        DownloadRecord::class,
        BookBookmarkEntity::class,
    ],
    version = 4,
    autoMigrations = [AutoMigration(from = 3, to = 4)],
)
abstract class BookDatabase : RoomDatabase() {
    abstract val bookProgressDao: BookProgressDao
    abstract val serverDao: ServerDao
    abstract val userDao: UserDao
    abstract val downloadRecordDao: DownloadRecordDao
    abstract val bookBookmarkDao: BookBookmarkDao
}
