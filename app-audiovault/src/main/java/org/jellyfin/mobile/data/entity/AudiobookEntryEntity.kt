package org.jellyfin.mobile.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audiobook_entries")
data class AudiobookEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "server_id") val serverId: Long,
    @ColumnInfo(name = "parent_id") val parentId: String?, // folder UUID for bundles; null for standalone files
    val title: String,
    val author: String?,
    @ColumnInfo(name = "image_item_id") val imageItemId: String?,
    @ColumnInfo(name = "image_tag") val imageTag: String?,
    @ColumnInfo(name = "runtime_ticks") val runtimeTicks: Long,
    @ColumnInfo(name = "track_ids") val trackIds: String, // comma-separated UUID strings, ordered
    @ColumnInfo(name = "is_bundle") val isBundle: Boolean,
    @ColumnInfo(
        name = "date_added",
    ) val dateAdded: String, // ISO-8601 string; lexicographic comparison works for sorting
    @ColumnInfo(name = "last_played") val lastPlayed: String, // ISO-8601 string; empty if never played
    @ColumnInfo(name = "played_percentage", defaultValue = "0") val playedPercentage: Float,
)
