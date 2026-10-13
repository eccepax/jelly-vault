package org.jellyfin.mobile.music.catalog

import org.jellyfin.mobile.music.domain.Album
import org.jellyfin.mobile.music.domain.Artist
import org.jellyfin.mobile.music.domain.Track

/**
 * Everything TuneVault reads from the server about music. The only implementation that touches the
 * Jellyfin SDK is [JellyfinMusicCatalog]; everything above this layer is tested against a fake.
 */
// The read-only browsing surface. Search, favorites and playlists get their own interfaces rather
// than growing this one.
@Suppress("TooManyFunctions")
interface MusicCatalog {
    /** All albums in the server's music libraries, sorted by title. */
    suspend fun albums(): List<Album>

    /** The album with this id, or null if there is no such album. */
    suspend fun album(albumId: String): Album?

    /** Every track in the server's music libraries. */
    suspend fun tracks(): List<Track>

    /** The album's tracks in play order (disc, then track number). */
    suspend fun albumTracks(albumId: String): List<Track>

    /** Album artists in the server's music libraries, sorted by name. */
    suspend fun albumArtists(): List<Artist>

    /**
     * A URL the player can stream the track from without extra headers (credentials included). The
     * server direct-streams formats the device plays and transcodes anything else.
     */
    fun streamUrl(trackId: String): String

    /** The artist with this id, or null if there is no such artist. */
    suspend fun artist(artistId: String): Artist?

    /** Albums by the given album artist, sorted by title. */
    suspend fun artistAlbums(artistId: String): List<Album>
}
