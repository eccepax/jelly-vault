package org.jellyfin.mobile.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.jellyfin.mobile.data.entity.AudiobookBookmarkEntity

@Dao
interface AudiobookBookmarkDao {
    @Insert
    suspend fun insert(bookmark: AudiobookBookmarkEntity)

    @Query("DELETE FROM audiobook_bookmark WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE audiobook_bookmark SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String?)

    /** The bookmarks in [trackIds] (a book's tracks), the newest first. */
    @Query("SELECT * FROM audiobook_bookmark WHERE track_id IN (:trackIds) ORDER BY created_at DESC")
    fun forTracks(trackIds: List<String>): Flow<List<AudiobookBookmarkEntity>>

    /** Forgets every bookmark (signing out: they're the account's). */
    @Query("DELETE FROM audiobook_bookmark")
    suspend fun clear()
}
