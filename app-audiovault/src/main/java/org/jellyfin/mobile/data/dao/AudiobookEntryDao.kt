package org.jellyfin.mobile.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.jellyfin.mobile.data.entity.AudiobookEntryEntity
import org.jellyfin.mobile.data.entity.SeparateBookEntity

@Dao
interface AudiobookEntryDao {
    @Query("SELECT * FROM audiobook_entries WHERE server_id = :serverId ORDER BY title ASC")
    suspend fun getAll(serverId: Long): List<AudiobookEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<AudiobookEntryEntity>)

    @Query("DELETE FROM audiobook_entries WHERE server_id = :serverId")
    suspend fun clearForServer(serverId: Long)

    /** The books of several files to show as separate books (see SeparateBookEntity), by entry id. */
    @Query("SELECT book_key FROM separate_book")
    fun separateBooks(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeparateBook(book: SeparateBookEntity)

    suspend fun showAsSeparateBooks(bookKey: String) = insertSeparateBook(SeparateBookEntity(bookKey))

    @Query("DELETE FROM separate_book WHERE book_key = :bookKey")
    suspend fun showAsOneBook(bookKey: String)
}
