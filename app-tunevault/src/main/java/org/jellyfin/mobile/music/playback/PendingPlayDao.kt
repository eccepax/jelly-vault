package org.jellyfin.mobile.music.playback

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/** A play the server hasn't heard about yet. */
@Entity(tableName = "pending_play")
data class PendingPlayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val playedAtMs: Long,
)

@Dao
interface PendingPlayDao {
    @Insert
    suspend fun insert(play: PendingPlayEntity)

    @Query("SELECT * FROM pending_play ORDER BY playedAtMs, id")
    suspend fun all(): List<PendingPlayEntity>

    @Query("DELETE FROM pending_play WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pending_play")
    suspend fun deleteAll()
}
