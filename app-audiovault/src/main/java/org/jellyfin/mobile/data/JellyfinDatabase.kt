package org.jellyfin.mobile.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import org.jellyfin.mobile.common.data.dao.DownloadRecordDao
import org.jellyfin.mobile.common.data.dao.ServerDao
import org.jellyfin.mobile.common.data.dao.UserDao
import org.jellyfin.mobile.common.data.entity.DownloadRecord
import org.jellyfin.mobile.common.data.entity.ServerEntity
import org.jellyfin.mobile.common.data.entity.UserEntity
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
        DownloadRecord::class,
        HiddenItemEntity::class,
    ],
    version = 7,
    autoMigrations = [
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
    ],
)
abstract class JellyfinDatabase : RoomDatabase() {
    abstract val serverDao: ServerDao
    abstract val userDao: UserDao
    abstract val downloadDao: DownloadDao
    abstract val audiobookEntryDao: AudiobookEntryDao
    abstract val downloadRecordDao: DownloadRecordDao
    abstract val hiddenItemDao: HiddenItemDao
}
