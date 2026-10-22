package org.jellyfin.mobile.books.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "book_progress")
data class BookProgressEntity(
    @PrimaryKey val bookItemId: String,
    val pageNumber: Int,
    val totalPages: Int,
    val lastUpdated: Long,
    /**
     * Not sent to the server yet (see [org.jellyfin.mobile.books.progress.BookProgressSync]). Places
     * saved before there was syncing never reached the server usably, so they start out unsent.
     */
    @ColumnInfo(defaultValue = "1") val pendingSync: Boolean = true,
)
