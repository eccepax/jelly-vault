package org.jellyfin.mobile.music.downloads

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.jellyfin.mobile.music.domain.Album
import org.jellyfin.mobile.music.domain.Playlist
import org.jellyfin.mobile.music.domain.Track

/** Records requests; statuses and downloaded songs are set by tests. */
class FakeCollectionDownloads : CollectionDownloads {
    override val statuses = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    override val collections = MutableStateFlow<List<DownloadedCollection>>(emptyList())
    override val downloadedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val requests = mutableListOf<String>()

    override fun status(collectionId: String): Flow<DownloadStatus> =
        statuses.map { it[collectionId] ?: DownloadStatus.NotDownloaded }

    override suspend fun downloadAlbum(album: Album, tracks: List<Track>) {
        requests += "album ${album.id}: ${tracks.joinToString(",") { it.id }}"
    }

    override suspend fun downloadPlaylist(playlist: Playlist, tracks: List<Track>) {
        requests += "playlist ${playlist.id}: ${tracks.joinToString(",") { it.id }}"
    }

    override suspend fun updatePlaylist(playlist: Playlist, tracks: List<Track>) {
        requests += "update ${playlist.id}: ${tracks.joinToString(",") { it.id }}"
    }

    override suspend fun remove(collectionId: String) {
        requests += "remove $collectionId"
    }

    override suspend fun retry(collectionId: String) {
        requests += "retry $collectionId"
    }
}
