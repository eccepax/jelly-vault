package org.jellyfin.mobile.music.downloads

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.jellyfin.mobile.music.domain.Track

@Entity(tableName = "download_collection")
data class DownloadCollectionEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val title: String,
    val artist: String?,
    val coverUrl: String?,
    val addedAt: Long,
)

/** A song's details, kept so downloaded music can be browsed with no connection. */
@Entity(tableName = "download_track")
data class DownloadTrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val albumId: String,
    val albumTitle: String,
    val durationMs: Long,
    val trackNumber: Int?,
    val discNumber: Int?,
    val coverUrl: String?,
    @ColumnInfo(defaultValue = "NULL") val normalizationGainDb: Float? = null,
) {
    fun toTrack() = Track(
        id,
        title,
        artist,
        albumId,
        albumTitle,
        durationMs,
        trackNumber,
        discNumber,
        coverUrl,
        normalizationGainDb = normalizationGainDb,
    )

    companion object {
        fun of(track: Track) = with(track) {
            DownloadTrackEntity(
                id,
                title,
                artist,
                albumId,
                albumTitle,
                durationMs,
                trackNumber,
                discNumber,
                coverUrl,
                normalizationGainDb,
            )
        }
    }
}

@Suppress("TooManyFunctions") // one per query; splitting would scatter them
@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_collection ORDER BY addedAt DESC, rowid DESC")
    fun collections(): Flow<List<DownloadCollectionEntity>>

    @Query("SELECT * FROM download_track")
    fun tracks(): Flow<List<DownloadTrackEntity>>

    /** Songs whose details no downloaded group needs any more (the groups live in common-downloads). */
    @Query("SELECT id FROM download_track WHERE id NOT IN (SELECT itemId FROM download_group_item)")
    suspend fun orphanTrackIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: DownloadCollectionEntity)

    @Upsert
    suspend fun upsertTracks(tracks: List<DownloadTrackEntity>)

    @Query("DELETE FROM download_collection WHERE id = :id")
    suspend fun deleteCollection(id: String)

    @Query("DELETE FROM download_track WHERE id IN (:ids)")
    suspend fun deleteTracks(ids: List<String>)

    @Query("DELETE FROM download_collection")
    suspend fun deleteAllCollections()

    /** Saves [collection]'s details and its songs' details; which songs it has is kept in its download group. */
    @Transaction
    suspend fun save(collection: DownloadCollectionEntity, tracks: List<Track>) {
        insertCollection(collection)
        upsertTracks(tracks.map(DownloadTrackEntity::of))
    }

    /** Deletes the details of songs nothing needs any more, returning their ids. */
    @Transaction
    suspend fun deleteOrphanTracks(): List<String> = orphanTrackIds().also { if (it.isNotEmpty()) deleteTracks(it) }
}
