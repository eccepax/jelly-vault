package org.jellyfin.mobile.music.offline

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.jellyfin.mobile.music.catalog.HomeFeed
import org.jellyfin.mobile.music.catalog.MusicCatalog
import org.jellyfin.mobile.music.catalog.MusicSearch
import org.jellyfin.mobile.music.catalog.SearchResults
import org.jellyfin.mobile.music.domain.Album
import org.jellyfin.mobile.music.domain.Artist
import org.jellyfin.mobile.music.domain.Playlist
import org.jellyfin.mobile.music.domain.PlaylistEntry
import org.jellyfin.mobile.music.domain.Track
import org.jellyfin.mobile.music.downloads.DownloadedCollection
import org.jellyfin.mobile.music.downloads.DownloadedCollection.Kind

/**
 * The music on the device, for when the Vault can't be reached. Only songs fully downloaded are
 * listed, so everything shown can play. Artists aren't browsable: downloads keep artist names, not
 * the server's artist pages.
 */
@Suppress("TooManyFunctions")
class DownloadedLibrary(
    private val collections: Flow<List<DownloadedCollection>>,
    private val downloadedTrackIds: Flow<Set<String>>,
) : MusicCatalog, HomeFeed, MusicSearch {

    /** Each collection with only its songs on the device. */
    private suspend fun available(): List<DownloadedCollection> {
        val downloaded = downloadedTrackIds.first()
        return collections.first().map { collection ->
            collection.copy(
                tracks = collection.tracks.filter { it.id in downloaded },
            )
        }
    }

    private suspend fun albumCollections() = available().filter { it.kind == Kind.ALBUM }

    override suspend fun albums(): List<Album> = albumCollections().map { it.toAlbum() }

    override suspend fun album(albumId: String): Album? = albumCollections().firstOrNull { it.id == albumId }?.toAlbum()

    override suspend fun tracks(): List<Track> = available().flatMap { it.tracks }.distinctBy { it.id }

    override suspend fun albumTracks(albumId: String): List<Track> =
        albumCollections().firstOrNull { it.id == albumId }?.tracks.orEmpty()

    override suspend fun albumArtists(): List<Artist> = emptyList()

    override suspend fun artist(artistId: String): Artist? = null

    override suspend fun artistAlbums(artistId: String): List<Album> = emptyList()

    /** Not used: streams are always addressed through the server's catalog, which finds downloads by id. */
    override fun streamUrl(trackId: String): String = error("Downloads are played through the server catalog's URLs")

    suspend fun playlists(): List<Playlist> = available().filter { it.kind == Kind.PLAYLIST }
        .map { Playlist(it.id, it.title, it.tracks.size, it.coverUrl) }

    suspend fun playlistEntries(playlistId: String): List<PlaylistEntry> =
        available().firstOrNull { it.id == playlistId && it.kind == Kind.PLAYLIST }?.tracks.orEmpty()
            .mapIndexed { index, track -> PlaylistEntry(entryId = "offline:$index", track = track) }

    override suspend fun recentlyPlayed(limit: Int): List<Track> = emptyList()

    /** Offline, Home's newest shelf shows the newest downloads. */
    override suspend fun recentlyAdded(limit: Int): List<Album> = albums().take(limit)

    override suspend fun search(query: String): SearchResults {
        val needle = query.trim()
        if (needle.isEmpty()) return SearchResults.EMPTY
        fun String?.matches() = this?.contains(needle, ignoreCase = true) == true
        return SearchResults(
            artists = emptyList(),
            albums = albums().filter { it.title.matches() || it.artist.matches() },
            tracks = tracks().filter { it.title.matches() || it.artist.matches() || it.albumTitle.matches() },
        )
    }

    private fun DownloadedCollection.toAlbum() = Album(id, title, artist.orEmpty(), year = null, coverUrl = coverUrl)
}
