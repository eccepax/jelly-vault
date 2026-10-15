package org.jellyfin.mobile.music.downloads

import kotlinx.coroutines.flow.Flow
import org.jellyfin.mobile.common.downloads.GroupStatus
import org.jellyfin.mobile.music.domain.Album
import org.jellyfin.mobile.music.domain.Playlist
import org.jellyfin.mobile.music.domain.Track

/** Downloading albums and playlists, as screens see it (see [DownloadRepository]). */
interface CollectionDownloads {
    val downloadedTrackIds: Flow<Set<String>>

    /** Downloaded albums and playlists, newest first. */
    val collections: Flow<List<DownloadedCollection>>

    /** Every downloaded album's and playlist's status, by id. */
    val statuses: Flow<Map<String, GroupStatus>>

    fun status(collectionId: String): Flow<GroupStatus>

    suspend fun downloadAlbum(album: Album, tracks: List<Track>)

    suspend fun downloadPlaylist(playlist: Playlist, tracks: List<Track>)

    /** Brings a downloaded playlist in line with the server; does nothing if it isn't downloaded. */
    suspend fun updatePlaylist(playlist: Playlist, tracks: List<Track>)

    suspend fun remove(collectionId: String)

    suspend fun retry(collectionId: String)
}
