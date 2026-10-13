package org.jellyfin.mobile.music.downloads

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.jellyfin.mobile.music.domain.Album
import org.jellyfin.mobile.music.domain.Playlist
import org.jellyfin.mobile.music.domain.Track
import org.jellyfin.mobile.music.downloads.TrackDownload.State

/**
 * Albums and playlists kept on the device. Each song is fetched once however many downloaded
 * albums and playlists include it, and deleted only when none of them needs it any more.
 */
class DownloadRepository(
    private val downloader: TrackDownloader,
    private val dao: DownloadDao,
    /** Where to fetch a song from, at the download quality. */
    private val downloadUri: (trackId: String) -> String,
    private val now: () -> Long = System::currentTimeMillis,
) : CollectionDownloads {
    override val collections: Flow<List<DownloadedCollection>> =
        combine(dao.collections(), dao.links(), dao.tracks()) { collections, links, tracks ->
            val tracksById = tracks.associateBy { it.id }
            val linksByCollection = links.groupBy { it.collectionId }
            collections.map { collection ->
                DownloadedCollection(
                    id = collection.id,
                    kind = DownloadedCollection.Kind.valueOf(collection.kind),
                    title = collection.title,
                    artist = collection.artist,
                    coverUrl = collection.coverUrl,
                    tracks = linksByCollection[collection.id].orEmpty()
                        .sortedBy { it.position }
                        .mapNotNull { tracksById[it.trackId]?.toTrack() },
                )
            }
        }

    /** Songs fully on the device. */
    override val downloadedTrackIds: Flow<Set<String>> =
        combine(dao.links(), downloader.downloads) { links, downloads ->
            links.mapTo(mutableSetOf()) { it.trackId }.filterTo(mutableSetOf()) { downloads[it]?.state == State.DONE }
        }.distinctUntilChanged()

    val bytesUsed: Flow<Long> = downloader.downloads.map { downloads -> downloads.values.sumOf { it.bytesDownloaded } }
        .distinctUntilChanged()

    override val statuses: Flow<Map<String, DownloadStatus>> =
        combine(dao.collections(), dao.links(), downloader.downloads) { collections, links, downloads ->
            val linksByCollection = links.groupBy { it.collectionId }
            collections.associate { collection ->
                val states = linksByCollection[collection.id].orEmpty().map { downloads[it.trackId]?.state }
                collection.id to statusOf(states)
            }
        }.distinctUntilChanged()

    override fun status(collectionId: String): Flow<DownloadStatus> =
        statuses.map { it[collectionId] ?: DownloadStatus.NotDownloaded }.distinctUntilChanged()

    private fun statusOf(states: List<State?>): DownloadStatus {
        val done = states.count { it == State.DONE }
        val failed = states.count { it == State.FAILED }
        return when {
            failed > 0 -> DownloadStatus.Failed(failed, states.size)
            done == states.size -> DownloadStatus.Downloaded
            State.WAITING in states -> DownloadStatus.Waiting(done, states.size)
            else -> DownloadStatus.InProgress(done, states.size)
        }
    }

    override suspend fun downloadAlbum(album: Album, tracks: List<Track>) =
        save(DownloadedCollection.Kind.ALBUM, album.id, album.title, album.artist, album.coverUrl, tracks)

    override suspend fun downloadPlaylist(playlist: Playlist, tracks: List<Track>) =
        save(DownloadedCollection.Kind.PLAYLIST, playlist.id, playlist.name, null, playlist.coverUrl, tracks)

    /** Brings a downloaded playlist in line with the server's version of it; others are left alone. */
    override suspend fun updatePlaylist(playlist: Playlist, tracks: List<Track>) {
        if (!dao.exists(playlist.id)) return
        if (dao.trackIdsOf(playlist.id) == tracks.map { it.id }) return
        downloadPlaylist(playlist, tracks)
        deleteUnneeded()
    }

    override suspend fun remove(collectionId: String) {
        dao.deleteCollection(collectionId)
        deleteUnneeded()
    }

    suspend fun removeAll() {
        dao.deleteAllCollections()
        deleteUnneeded()
        downloader.downloads.value.keys.forEach(downloader::remove)
    }

    /** Fetches again the songs of [collectionId] that failed. */
    override suspend fun retry(collectionId: String) {
        val downloads = downloader.downloads.value
        dao.trackIdsOf(collectionId).filter { downloads[it]?.state == State.FAILED }.forEach(::fetch)
    }

    /** At launch: fetches again any song a download needs but the downloader no longer has. */
    suspend fun repair() {
        downloader.isReady.first { it }
        val downloads = downloader.downloads.value
        dao.allTrackIds().filter { it !in downloads }.forEach(::fetch)
    }

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
        val downloads = downloader.downloads.value
        tracks.map { it.id }.distinct().filter { it !in downloads }.forEach(::fetch)
    }

    private suspend fun deleteUnneeded() {
        dao.deleteOrphanTracks().forEach(downloader::remove)
    }

    private fun fetch(trackId: String) = downloader.download(trackId, downloadUri(trackId))
}
