package org.jellyfin.mobile.common.downloads

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Something downloaded as a whole: an album, a playlist, an audiobook, a book. */
@Entity(tableName = "download_group")
data class DownloadGroupEntity(@PrimaryKey val id: String, val addedAt: Long)

/** The item at [position] in group [groupId]; one item can be in several groups. */
@Entity(
    tableName = "download_group_item",
    primaryKeys = ["groupId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = DownloadGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("itemId")],
)
data class DownloadGroupItemEntity(val groupId: String, val position: Int, val itemId: String)

/** The download-group tables. An app adds both entities and this DAO to its own Room database. */
@Suppress("TooManyFunctions") // one per query; splitting would scatter them
@Dao
interface DownloadGroupDao {
    @Query("SELECT * FROM download_group ORDER BY addedAt DESC, rowid DESC")
    fun groups(): Flow<List<DownloadGroupEntity>>

    @Query("SELECT * FROM download_group_item ORDER BY groupId, position")
    fun items(): Flow<List<DownloadGroupItemEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM download_group WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    @Query("SELECT itemId FROM download_group_item WHERE groupId = :id ORDER BY position")
    suspend fun itemIdsOf(id: String): List<String>

    @Query("SELECT DISTINCT itemId FROM download_group_item")
    suspend fun allItemIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: DownloadGroupEntity)

    @Insert
    suspend fun insertItems(items: List<DownloadGroupItemEntity>)

    @Query("DELETE FROM download_group_item WHERE groupId = :id")
    suspend fun deleteItems(id: String)

    @Query("DELETE FROM download_group WHERE id = :id")
    suspend fun deleteGroup(id: String)

    @Query("DELETE FROM download_group")
    suspend fun deleteAllGroups()

    /** Saves group [id] with exactly [itemIds], in order, replacing any earlier list. */
    @Transaction
    suspend fun save(id: String, addedAt: Long, itemIds: List<String>) {
        insertGroup(DownloadGroupEntity(id, addedAt))
        deleteItems(id)
        insertItems(itemIds.mapIndexed { position, itemId -> DownloadGroupItemEntity(id, position, itemId) })
    }
}
