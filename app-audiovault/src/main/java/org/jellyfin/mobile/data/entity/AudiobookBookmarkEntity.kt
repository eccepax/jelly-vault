package org.jellyfin.mobile.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A place the listener bookmarked: [positionMs] into the track [trackId], named by [label] (its
 * chapter and time), with an optional [note]. Kept on the device; signing out forgets it.
 */
@Entity(tableName = "audiobook_bookmark")
data class AudiobookBookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "track_id", index = true) val trackId: String,
    @ColumnInfo(name = "position_ms") val positionMs: Long,
    val label: String,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
