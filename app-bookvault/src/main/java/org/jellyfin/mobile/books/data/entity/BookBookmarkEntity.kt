package org.jellyfin.mobile.books.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-created bookmark within a book — distinct from [BookProgressEntity]'s single auto-resume
 * position: a book can have any number of these.
 */
@Entity(tableName = "book_bookmark")
data class BookBookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookItemId: String,
    val pageIndex: Int,
    val label: String,
    val note: String?,
    val createdAt: Long,
)
