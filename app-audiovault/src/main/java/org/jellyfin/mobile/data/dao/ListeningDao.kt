package org.jellyfin.mobile.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.jellyfin.mobile.data.entity.ListeningDayEntity

/** Time listened, by day, kept on the device for the stats (see stats.ListeningLog). */
@Dao
interface ListeningDao {
    @Query("INSERT OR IGNORE INTO listening_day (day, listened_ms) VALUES (:day, 0)")
    suspend fun startDay(day: String)

    @Query("UPDATE listening_day SET listened_ms = listened_ms + :ms WHERE day = :day")
    suspend fun addToDay(day: String, ms: Long)

    /** Adds [ms] listened to [day] (an ISO date). */
    @Transaction
    suspend fun addListened(day: String, ms: Long) {
        startDay(day)
        addToDay(day, ms)
    }

    @Query("SELECT * FROM listening_day")
    fun days(): Flow<List<ListeningDayEntity>>

    /** Forgets the time listened (signing out: it's the account's). */
    @Query("DELETE FROM listening_day")
    suspend fun clearDays()
}
