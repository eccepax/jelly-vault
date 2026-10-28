package org.jellyfin.mobile.music.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
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
import org.jellyfin.mobile.music.downloads.DownloadCollectionEntity
import org.jellyfin.mobile.music.downloads.DownloadDao
import org.jellyfin.mobile.music.downloads.DownloadTrackEntity
import org.jellyfin.mobile.music.playback.PendingPlayDao
import org.jellyfin.mobile.music.playback.PendingPlayEntity

@Database(
    entities = [
        ServerEntity::class,
        UserEntity::class,
        DownloadCollectionEntity::class,
        DownloadTrackEntity::class,
        DownloadGroupEntity::class,
        DownloadGroupItemEntity::class,
        PendingPlayEntity::class,
    ],
    version = 7,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5, spec = TuneDatabase.SharedDownloads::class),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7, spec = TuneDatabase.NoDownloadContainer::class),
    ],
)
abstract class TuneDatabase : RoomDatabase() {
    abstract val serverDao: ServerDao
    abstract val userDao: UserDao
    abstract val downloadDao: DownloadDao
    abstract val downloadGroupDao: DownloadGroupDao
    abstract val pendingPlayDao: PendingPlayDao

    /**
     * Downloads moved to the shared download groups and plain files. The old song files can't be
     * carried over, so downloaded albums and playlists are forgotten and downloaded again by hand.
     */
    @DeleteTable(tableName = "download_collection_track")
    class SharedDownloads : AutoMigrationSpec {
        // Room calls one or the other, depending on whether the database runs on an SQLite driver.
        override fun onPostMigrate(db: SupportSQLiteDatabase) = FORGET_DOWNLOADS.forEach(db::execSQL)

        override fun onPostMigrate(connection: SQLiteConnection) = FORGET_DOWNLOADS.forEach(connection::execSQL)

        private companion object {
            val FORGET_DOWNLOADS = listOf("DELETE FROM download_collection", "DELETE FROM download_track")
        }
    }

    /**
     * Downloaded songs' containers were kept for casting them (version 6), which now asks the server
     * what each file is when it's cast.
     */
    @DeleteColumn(tableName = "download_track", columnName = "container")
    class NoDownloadContainer : AutoMigrationSpec
}
