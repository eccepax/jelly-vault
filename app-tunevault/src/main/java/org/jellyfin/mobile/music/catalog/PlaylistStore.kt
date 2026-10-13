package org.jellyfin.mobile.music.catalog

import org.jellyfin.mobile.music.domain.Playlist
import org.jellyfin.mobile.music.domain.PlaylistEntry

/**
 * The user's playlists, kept on the server.
 *
 * Callers must not add a song a playlist already has. Jellyfin's handling of repeats isn't dependable
 * (found by the contract tests): creating a playlist dropped repeated songs, re-adding a song added
 * a second copy on one run and was ignored on later ones, and copies share one entry id, so removing
 * one removes them all.
 */
interface PlaylistStore {
    /** Music playlists, by name. */
    suspend fun playlists(): List<Playlist>

    /** The playlist's songs, in its order. */
    suspend fun playlistEntries(playlistId: String): List<PlaylistEntry>

    /** Creates a playlist (optionally with songs already in it) and returns its id. */
    suspend fun createPlaylist(name: String, trackIds: List<String>): String

    suspend fun addToPlaylist(playlistId: String, trackIds: List<String>)
    suspend fun removeFromPlaylist(playlistId: String, entryId: String)
    suspend fun deletePlaylist(playlistId: String)
}
