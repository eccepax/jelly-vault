package org.jellyfin.mobile.common.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_record")
data class DownloadRecord(
    @PrimaryKey val itemId: String,
    val title: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val downloadedAt: Long,
    val mediaType: String,
)
