package org.jellyfin.mobile.books.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteTable
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import org.jellyfin.mobile.books.data.dao.BookBookmarkDao
import org.jellyfin.mobile.books.data.dao.BookProgressDao
import org.jellyfin.mobile.books.data.entity.BookBookmarkEntity
import org.jellyfin.mobile.books.data.entity.BookProgressEntity
import org.jellyfin.mobile.common.data.dao.ServerDao
import org.jellyfin.mobile.common.data.dao.UserDao
import org.jellyfin.mobile.common.data.entity.ServerEntity
import org.jellyfin.mobile.common.data.entity.UserEntity
import org.jellyfin.mobile.common.downloads.DownloadGroupDao
import org.jellyfin.mobile.common.downloads.DownloadGroupEntity
import org.jellyfin.mobile.common.downloads.DownloadGroupItemEntity

@Database(
    entities = [
        BookProgressEntity::class,
        ServerEntity::class,
        UserEntity::class,
        BookBookmarkEntity::class,
        DownloadGroupEntity::class,
        DownloadGroupItemEntity::class,
    ],
    version = 5,
    autoMigrations = [
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5, spec = BookDatabase.SharedDownloads::class),
    ],
)
abstract class BookDatabase : RoomDatabase() {
    abstract val bookProgressDao: BookProgressDao
    abstract val serverDao: ServerDao
    abstract val userDao: UserDao
    abstract val bookBookmarkDao: BookBookmarkDao
    abstract val downloadGroupDao: DownloadGroupDao

    /**
     * Downloads moved to the shared download groups. Books downloaded before can't be carried over,
     * so they are downloaded again by hand; reading progress and bookmarks are kept.
     */
    @DeleteTable(tableName = "download_record")
    class SharedDownloads : AutoMigrationSpec
}
