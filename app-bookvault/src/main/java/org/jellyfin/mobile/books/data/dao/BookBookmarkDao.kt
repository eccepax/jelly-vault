package org.jellyfin.mobile.books.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.jellyfin.mobile.books.data.entity.BookBookmarkEntity

@Dao
interface BookBookmarkDao {
    @Insert
    suspend fun insert(entity: BookBookmarkEntity)

    @Query("DELETE FROM book_bookmark WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM book_bookmark WHERE bookItemId = :bookItemId ORDER BY createdAt DESC")
    fun getForBook(bookItemId: String): Flow<List<BookBookmarkEntity>>
}
