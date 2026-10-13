package org.jellyfin.mobile.books.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "book_progress")
data class BookProgressEntity(
    @PrimaryKey val bookItemId: String,
    val pageNumber: Int,
    val totalPages: Int,
    val lastUpdated: Long,
)
