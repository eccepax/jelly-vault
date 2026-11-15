package org.jellyfin.mobile.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.jellyfin.mobile.data.entity.FinishedBookEntity

/** The books played to the end, kept on the device for the stats (see stats.ListeningLog). */
@Dao
interface FinishedBookDao {
    /** Keeps [book] as finished, a book finished before as finished again. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun bookFinished(book: FinishedBookEntity)

    /** The books finished, the latest first. */
    @Query("SELECT * FROM finished_book ORDER BY finished_at DESC")
    fun finishedBooks(): Flow<List<FinishedBookEntity>>

    /** Forgets them (signing out: they're the account's). */
    @Query("DELETE FROM finished_book")
    suspend fun clear()
}
