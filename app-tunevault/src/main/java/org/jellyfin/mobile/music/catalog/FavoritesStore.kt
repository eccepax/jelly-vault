package org.jellyfin.mobile.music.catalog

import org.jellyfin.mobile.music.domain.Album
import org.jellyfin.mobile.music.domain.Track

/** The user's favorites, kept on the server so every Jellyfin client agrees. */
interface FavoritesStore {
    suspend fun setFavorite(itemId: String, favorite: Boolean)
    suspend fun favoriteTracks(): List<Track>
    suspend fun favoriteAlbums(): List<Album>
}
