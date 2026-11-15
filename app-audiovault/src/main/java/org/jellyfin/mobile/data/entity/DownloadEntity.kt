package org.jellyfin.mobile.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.Json.Default.decodeFromString
import org.jellyfin.mobile.data.entity.DownloadEntity.Key.ITEM_ID
import org.jellyfin.mobile.data.entity.DownloadEntity.Key.TABLE_NAME
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.jellyfin.mobile.utils.extensions.toFileSize
import kotlin.time.Duration

@Entity(
    tableName = TABLE_NAME,
    indices = [
        Index(value = [ITEM_ID], unique = true),
    ],
)
@TypeConverters(LocalJellyfinMediaSourceConverter::class)
data class DownloadEntity(
    @PrimaryKey
    @ColumnInfo(name = ITEM_ID)
    val itemId: String,
    @ColumnInfo(name = MEDIA_SOURCE)
    val mediaSource: LocalJellyfinMediaSource,
) {
    /** The download to play, from [startTime] when given, else from where the listener left it. */
    fun asMediaSource(startTime: Duration? = null): LocalJellyfinMediaSource =
        mediaSource.also { source -> startTime?.let { source.startTime = it } }

    constructor(mediaSource: LocalJellyfinMediaSource) :
        this(mediaSource.id, mediaSource)

    @Ignore
    val fileSize: String = mediaSource.downloadSize.toFileSize()

    companion object Key {
        const val BYTES_PER_BINARY_UNIT: Int = 1024
        const val TABLE_NAME: String = "Download"
        const val ID: String = "id"
        const val ITEM_ID: String = "item_id"
        const val MEDIA_SOURCE: String = "media_source"
    }
}

class LocalJellyfinMediaSourceConverter {
    @TypeConverter
    fun toLocalJellyfinMediaSource(value: String): LocalJellyfinMediaSource = decodeFromString(value)

    @TypeConverter
    fun fromLocalJellyfinMediaSource(value: LocalJellyfinMediaSource): String = Json.encodeToString(value)
}
