package org.jellyfin.mobile.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.jellyfin.mobile.data.entity.HiddenItemEntity

@Dao
interface HiddenItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: HiddenItemEntity)

    @Query("DELETE FROM hidden_item WHERE itemId = :itemId")
    suspend fun delete(itemId: String)

    @Query("SELECT * FROM hidden_item")
    fun getAll(): Flow<List<HiddenItemEntity>>
}
