package org.jellyfin.mobile.music.catalog

import kotlinx.coroutines.CompletableDeferred
import org.jellyfin.mobile.music.domain.Album
import org.jellyfin.mobile.music.domain.Artist
import org.jellyfin.mobile.music.domain.Track

/** In-memory [MusicCatalog] for ViewModel/UI tests. */
class FakeMusicCatalog(
    var albums: List<Album> = emptyList(),
    var tracksByAlbum: Map<String, List<Track>> = emptyMap(),
    var artists: List<Artist> = emptyList(),
) : MusicCatalog {
    /** When set, every call throws it. */
    var failure: Throwable? = null

    /** When set, every call suspends until it completes — for observing loading states. */
    var gate: CompletableDeferred<Unit>? = null

    var albumsCalls = 0
        private set

    override suspend fun albums(): List<Album> = respond {
        albumsCalls++
        albums
    }

    override suspend fun album(albumId: String): Album? = respond { albums.firstOrNull { it.id == albumId } }

    override suspend fun tracks(): List<Track> = respond { tracksByAlbum.values.flatten() }

    override suspend fun albumTracks(albumId: String): List<Track> = respond { tracksByAlbum[albumId].orEmpty() }

    override fun streamUrl(trackId: String): String = "https://vault.test/stream/$trackId"

    override suspend fun albumArtists(): List<Artist> = respond { artists }

    override suspend fun artist(artistId: String): Artist? = respond { artists.firstOrNull { it.id == artistId } }

    override suspend fun artistAlbums(artistId: String): List<Album> = respond {
        val name = artists.firstOrNull { it.id == artistId }?.name
        albums.filter { it.artist == name }
    }

    private suspend fun <T> respond(result: () -> T): T {
        gate?.await()
        failure?.let { throw it }
        return result()
    }
}

fun album(id: String, title: String = "Album $id", artist: String = "Artist", year: Int? = 2020) =
    Album(id = id, title = title, artist = artist, year = year)
