package org.jellyfin.mobile.books.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import org.jellyfin.mobile.books.data.entity.BookProgressEntity

@Dao
interface BookProgressDao {
    @Query("SELECT * FROM book_progress WHERE bookItemId = :bookItemId")
    suspend fun getProgress(bookItemId: String): BookProgressEntity?

    @Upsert
    suspend fun upsertProgress(entity: BookProgressEntity)

    @Query("SELECT * FROM book_progress")
    suspend fun getAllProgress(): List<BookProgressEntity>

    /** Places the server hasn't been told about, oldest first. */
    @Query("SELECT * FROM book_progress WHERE pendingSync = 1 ORDER BY lastUpdated")
    suspend fun getPending(): List<BookProgressEntity>

    /** Marks the place saved at [lastUpdated] as sent, unless a newer one has been saved since. */
    @Query("UPDATE book_progress SET pendingSync = 0 WHERE bookItemId = :bookItemId AND lastUpdated = :lastUpdated")
    suspend fun markSent(bookItemId: String, lastUpdated: Long)
}
