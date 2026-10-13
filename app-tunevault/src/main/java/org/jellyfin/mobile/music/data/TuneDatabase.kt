package org.jellyfin.mobile.music.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import org.jellyfin.mobile.common.data.dao.ServerDao
import org.jellyfin.mobile.common.data.dao.UserDao
import org.jellyfin.mobile.common.data.entity.ServerEntity
import org.jellyfin.mobile.common.data.entity.UserEntity
import org.jellyfin.mobile.music.downloads.DownloadCollectionEntity
import org.jellyfin.mobile.music.downloads.DownloadDao
import org.jellyfin.mobile.music.downloads.DownloadLinkEntity
import org.jellyfin.mobile.music.downloads.DownloadTrackEntity
import org.jellyfin.mobile.music.playback.PendingPlayDao
import org.jellyfin.mobile.music.playback.PendingPlayEntity

@Database(
    entities = [
        ServerEntity::class,
        UserEntity::class,
        DownloadCollectionEntity::class,
        DownloadTrackEntity::class,
        DownloadLinkEntity::class,
        PendingPlayEntity::class,
    ],
    version = 4,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class TuneDatabase : RoomDatabase() {
    abstract val serverDao: ServerDao
    abstract val userDao: UserDao
    abstract val downloadDao: DownloadDao
    abstract val pendingPlayDao: PendingPlayDao
}
