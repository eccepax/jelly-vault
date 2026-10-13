package org.jellyfin.mobile.music.downloads

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
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

@Entity(
    tableName = "download_collection_track",
    primaryKeys = ["collectionId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = DownloadCollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trackId")],
)
data class DownloadLinkEntity(val collectionId: String, val position: Int, val trackId: String)

@Suppress("TooManyFunctions") // one per query; splitting would scatter them
@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_collection ORDER BY addedAt DESC, rowid DESC")
    fun collections(): Flow<List<DownloadCollectionEntity>>

    @Query("SELECT * FROM download_collection_track ORDER BY collectionId, position")
    fun links(): Flow<List<DownloadLinkEntity>>

    @Query("SELECT * FROM download_track")
    fun tracks(): Flow<List<DownloadTrackEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM download_collection WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    @Query("SELECT trackId FROM download_collection_track WHERE collectionId = :id ORDER BY position")
    suspend fun trackIdsOf(id: String): List<String>

    @Query("SELECT DISTINCT trackId FROM download_collection_track")
    suspend fun allTrackIds(): List<String>

    @Query("SELECT id FROM download_track WHERE id NOT IN (SELECT trackId FROM download_collection_track)")
    suspend fun orphanTrackIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: DownloadCollectionEntity)

    @Upsert
    suspend fun upsertTracks(tracks: List<DownloadTrackEntity>)

    @Insert
    suspend fun insertLinks(links: List<DownloadLinkEntity>)

    @Query("DELETE FROM download_collection_track WHERE collectionId = :id")
    suspend fun deleteLinks(id: String)

    @Query("DELETE FROM download_collection WHERE id = :id")
    suspend fun deleteCollection(id: String)

    @Query("DELETE FROM download_track WHERE id IN (:ids)")
    suspend fun deleteTracks(ids: List<String>)

    @Query("DELETE FROM download_collection")
    suspend fun deleteAllCollections()

    /** Saves [collection] with exactly [tracks], in order, replacing any earlier song list. */
    @Transaction
    suspend fun save(collection: DownloadCollectionEntity, tracks: List<Track>) {
        insertCollection(collection)
        upsertTracks(tracks.map(DownloadTrackEntity::of))
        deleteLinks(collection.id)
        insertLinks(tracks.mapIndexed { position, track -> DownloadLinkEntity(collection.id, position, track.id) })
    }

    /** Deletes the details of songs nothing needs any more, returning their ids. */
    @Transaction
    suspend fun deleteOrphanTracks(): List<String> = orphanTrackIds().also { if (it.isNotEmpty()) deleteTracks(it) }
}
