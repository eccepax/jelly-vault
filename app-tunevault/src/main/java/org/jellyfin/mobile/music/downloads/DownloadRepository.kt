package org.jellyfin.mobile.music.downloads

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.jellyfin.mobile.common.downloads.DownloadGroups
import org.jellyfin.mobile.common.downloads.GroupStatus
import org.jellyfin.mobile.music.domain.Album
import org.jellyfin.mobile.music.domain.Playlist
import org.jellyfin.mobile.music.domain.Track

/**
 * Albums and playlists kept on the device: each is a download group of songs (see [DownloadGroups]),
 * so a song is fetched once however many of them include it. Their details and their songs' details
 * are kept here too, for browsing downloaded music with no connection.
 */
class DownloadRepository(
    private val groups: DownloadGroups,
    private val dao: DownloadDao,
    private val now: () -> Long = System::currentTimeMillis,
) : CollectionDownloads {
    override val collections: Flow<List<DownloadedCollection>> =
        combine(dao.collections(), groups.groups, dao.tracks()) { collections, groups, tracks ->
            val tracksById = tracks.associateBy { it.id }
            val trackIdsByGroup = groups.associate { it.id to it.itemIds }
            collections.map { collection ->
                DownloadedCollection(
                    id = collection.id,
                    kind = DownloadedCollection.Kind.valueOf(collection.kind),
                    title = collection.title,
                    artist = collection.artist,
                    coverUrl = collection.coverUrl,
                    tracks = trackIdsByGroup[collection.id].orEmpty().mapNotNull { tracksById[it]?.toTrack() },
                )
            }
        }

    /** Songs fully on the device. */
    override val downloadedTrackIds: Flow<Set<String>> = groups.downloadedItemIds

    val bytesUsed: Flow<Long> = groups.bytesUsed

    override val statuses: Flow<Map<String, GroupStatus>> = groups.statuses

    override fun status(collectionId: String): Flow<GroupStatus> = groups.status(collectionId)

    override suspend fun downloadAlbum(album: Album, tracks: List<Track>) =
        save(DownloadedCollection.Kind.ALBUM, album.id, album.title, album.artist, album.coverUrl, tracks)

    override suspend fun downloadPlaylist(playlist: Playlist, tracks: List<Track>) =
        save(DownloadedCollection.Kind.PLAYLIST, playlist.id, playlist.name, null, playlist.coverUrl, tracks)

    /** Brings a downloaded playlist in line with the server's version of it; others are left alone. */
    override suspend fun updatePlaylist(playlist: Playlist, tracks: List<Track>) {
        if (!groups.contains(playlist.id)) return
        if (groups.itemIdsOf(playlist.id) == tracks.map { it.id }) return
        downloadPlaylist(playlist, tracks)
    }

    override suspend fun remove(collectionId: String) {
        groups.remove(collectionId)
        dao.deleteCollection(collectionId)
        dao.deleteOrphanTracks()
    }

    suspend fun removeAll() {
        groups.removeAll()
        dao.deleteAllCollections()
        dao.deleteOrphanTracks()
    }

    /** Fetches again the songs of [collectionId] that failed. */
    override suspend fun retry(collectionId: String) = groups.retry(collectionId)

    /** At launch: fetches again any song a download needs but the downloader no longer has. */
    suspend fun repair() = groups.repair()

    @Suppress("LongParameterList")
    private suspend fun save(
        kind: DownloadedCollection.Kind,
        id: String,
        title: String,
        artist: String?,
        coverUrl: String?,
        tracks: List<Track>,
    ) {
        dao.save(DownloadCollectionEntity(id, kind.name, title, artist, coverUrl, addedAt = now()), tracks)
        groups.save(id, tracks.map { it.id })
        dao.deleteOrphanTracks()
    }
}
