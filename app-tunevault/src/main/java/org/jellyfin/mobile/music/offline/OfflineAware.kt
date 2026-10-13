package org.jellyfin.mobile.music.offline

import org.jellyfin.mobile.music.catalog.FavoritesStore
import org.jellyfin.mobile.music.catalog.HomeFeed
import org.jellyfin.mobile.music.catalog.MusicCatalog
import org.jellyfin.mobile.music.catalog.MusicSearch
import org.jellyfin.mobile.music.catalog.PlaylistStore
import org.jellyfin.mobile.music.catalog.SearchResults
import org.jellyfin.mobile.music.domain.Album
import org.jellyfin.mobile.music.domain.Artist
import org.jellyfin.mobile.music.domain.Playlist
import org.jellyfin.mobile.music.domain.PlaylistEntry
import org.jellyfin.mobile.music.domain.Track

// The app's view of the library: the server's while it can be reached, the downloads' when it can't
// (see ServerConnectivity). Changes (playlists, favorites) always go to the server, so they fail
// offline rather than being lost.

@Suppress("TooManyFunctions")
class OfflineAwareMusicCatalog(
    private val server: MusicCatalog,
    private val downloads: DownloadedLibrary,
    private val connectivity: ServerConnectivity,
) : MusicCatalog {
    override suspend fun albums(): List<Album> = connectivity.choose({ server.albums() }, { downloads.albums() })
    override suspend fun album(albumId: String): Album? =
        connectivity.choose({ server.album(albumId) }, { downloads.album(albumId) })
    override suspend fun tracks(): List<Track> = connectivity.choose({ server.tracks() }, { downloads.tracks() })
    override suspend fun albumTracks(albumId: String): List<Track> =
        connectivity.choose({ server.albumTracks(albumId) }, { downloads.albumTracks(albumId) })
    override suspend fun albumArtists(): List<Artist> =
        connectivity.choose({ server.albumArtists() }, { downloads.albumArtists() })
    override suspend fun artist(artistId: String): Artist? =
        connectivity.choose({ server.artist(artistId) }, { downloads.artist(artistId) })
    override suspend fun artistAlbums(artistId: String): List<Album> =
        connectivity.choose({ server.artistAlbums(artistId) }, { downloads.artistAlbums(artistId) })

    /** Always the server's: the player plays a download in its place, matched by song id. */
    override fun streamUrl(trackId: String): String = server.streamUrl(trackId)
}

class OfflineAwarePlaylistStore(
    private val server: PlaylistStore,
    private val downloads: DownloadedLibrary,
    private val connectivity: ServerConnectivity,
) : PlaylistStore by server {
    override suspend fun playlists(): List<Playlist> = connectivity.choose(
        { server.playlists() },
        { downloads.playlists() },
    )
    override suspend fun playlistEntries(playlistId: String): List<PlaylistEntry> =
        connectivity.choose({ server.playlistEntries(playlistId) }, { downloads.playlistEntries(playlistId) })
}

class OfflineAwareHomeFeed(
    private val server: HomeFeed,
    private val downloads: DownloadedLibrary,
    private val connectivity: ServerConnectivity,
) : HomeFeed {
    override suspend fun recentlyPlayed(limit: Int): List<Track> =
        connectivity.choose({ server.recentlyPlayed(limit) }, { downloads.recentlyPlayed(limit) })
    override suspend fun recentlyAdded(limit: Int): List<Album> =
        connectivity.choose({ server.recentlyAdded(limit) }, { downloads.recentlyAdded(limit) })
}

class OfflineAwareMusicSearch(
    private val server: MusicSearch,
    private val downloads: DownloadedLibrary,
    private val connectivity: ServerConnectivity,
) : MusicSearch {
    override suspend fun search(query: String): SearchResults =
        connectivity.choose({ server.search(query) }, { downloads.search(query) })
}

/** Offline there are no favorites to list; marking one still goes to the server (and fails). */
class OfflineAwareFavoritesStore(
    private val server: FavoritesStore,
    private val connectivity: ServerConnectivity,
) : FavoritesStore by server {
    override suspend fun favoriteTracks(): List<Track> = connectivity.choose(
        { server.favoriteTracks() },
        { emptyList() },
    )
    override suspend fun favoriteAlbums(): List<Album> = connectivity.choose(
        { server.favoriteAlbums() },
        { emptyList() },
    )
}
