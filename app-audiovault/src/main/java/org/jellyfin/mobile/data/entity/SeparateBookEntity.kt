package org.jellyfin.mobile.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A book of several files the listener asked to see as separate books (a series), by its entry id
 * (see AudiobookEntryBuilder.build). Kept on the device: no tag on the server tells such a book
 * (one book per file) from one book in parts.
 */
@Entity(tableName = "separate_book")
data class SeparateBookEntity(
    @PrimaryKey @ColumnInfo(name = "book_key") val bookKey: String,
)
