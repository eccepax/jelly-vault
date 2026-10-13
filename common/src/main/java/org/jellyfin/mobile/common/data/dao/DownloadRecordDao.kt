package org.jellyfin.mobile.common.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.jellyfin.mobile.common.data.entity.DownloadRecord

@Dao
interface DownloadRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: DownloadRecord)

    @Query("DELETE FROM download_record WHERE itemId = :itemId")
    suspend fun delete(itemId: String)

    @Query("SELECT * FROM download_record")
    fun getAll(): Flow<List<DownloadRecord>>

    @Query("SELECT EXISTS(SELECT 1 FROM download_record WHERE itemId = :itemId)")
    suspend fun exists(itemId: String): Boolean
}
