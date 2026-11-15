package org.jellyfin.mobile.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** How long books played on [day] (an ISO date, the device's), for the listening stats. */
@Entity(tableName = "listening_day")
data class ListeningDayEntity(
    @PrimaryKey val day: String,
    @ColumnInfo(name = "listened_ms") val listenedMs: Long,
)

/** A book played to the end of its last part, by [bookKey] (its first track), finished last at [finishedAt]. */
@Entity(tableName = "finished_book")
data class FinishedBookEntity(
    @PrimaryKey @ColumnInfo(name = "book_key") val bookKey: String,
    val title: String,
    @ColumnInfo(name = "finished_at") val finishedAt: Long,
)
