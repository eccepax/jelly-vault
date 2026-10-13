package org.jellyfin.mobile.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.jellyfin.mobile.data.entity.AudiobookEntryEntity

@Dao
interface AudiobookEntryDao {
    @Query("SELECT * FROM audiobook_entries WHERE server_id = :serverId ORDER BY title ASC")
    suspend fun getAll(serverId: Long): List<AudiobookEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<AudiobookEntryEntity>)

    @Query("DELETE FROM audiobook_entries WHERE server_id = :serverId")
    suspend fun clearForServer(serverId: Long)
}
