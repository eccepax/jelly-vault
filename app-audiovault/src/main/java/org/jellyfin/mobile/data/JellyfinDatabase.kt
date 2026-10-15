package org.jellyfin.mobile.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteTable
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.execSQL
import org.jellyfin.mobile.common.data.dao.ServerDao
import org.jellyfin.mobile.common.data.dao.UserDao
import org.jellyfin.mobile.common.data.entity.ServerEntity
import org.jellyfin.mobile.common.data.entity.UserEntity
import org.jellyfin.mobile.common.downloads.DownloadGroupDao
import org.jellyfin.mobile.common.downloads.DownloadGroupEntity
import org.jellyfin.mobile.common.downloads.DownloadGroupItemEntity
import org.jellyfin.mobile.data.dao.AudiobookEntryDao
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.dao.HiddenItemDao
import org.jellyfin.mobile.data.entity.AudiobookEntryEntity
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.data.entity.HiddenItemEntity

@Database(
    entities = [
        ServerEntity::class,
        UserEntity::class,
        DownloadEntity::class,
        AudiobookEntryEntity::class,
        HiddenItemEntity::class,
        DownloadGroupEntity::class,
        DownloadGroupItemEntity::class,
    ],
    version = 8,
    autoMigrations = [
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8, spec = JellyfinDatabase.SharedDownloads::class),
    ],
)
abstract class JellyfinDatabase : RoomDatabase() {
    abstract val serverDao: ServerDao
    abstract val userDao: UserDao
    abstract val downloadDao: DownloadDao
    abstract val audiobookEntryDao: AudiobookEntryDao
    abstract val hiddenItemDao: HiddenItemDao
    abstract val downloadGroupDao: DownloadGroupDao

    /**
     * Downloads moved to the shared download groups and plain files. Tracks downloaded before lived
     * in Media3's cache, which nothing reads now, so their details are forgotten too and books are
     * downloaded again by hand.
     */
    @DeleteTable(tableName = "download_record")
    class SharedDownloads : AutoMigrationSpec {
        // Room calls one or the other, depending on whether the database runs on an SQLite driver.
        override fun onPostMigrate(db: SupportSQLiteDatabase) = db.execSQL(FORGET_DOWNLOADS)

        override fun onPostMigrate(connection: SQLiteConnection) = connection.execSQL(FORGET_DOWNLOADS)

        private companion object {
            const val FORGET_DOWNLOADS = "DELETE FROM Download"
        }
    }
}
