package org.jellyfin.mobile.music.catalog

import org.jellyfin.mobile.music.domain.Album
import org.jellyfin.mobile.music.domain.Track

class FakeFavoritesStore(
    var tracks: List<Track> = emptyList(),
    var albums: List<Album> = emptyList(),
) : FavoritesStore {
    val writes = mutableListOf<String>()
    var failure: Throwable? = null

    override suspend fun setFavorite(itemId: String, favorite: Boolean) {
        failure?.let { throw it }
        writes += "$itemId=$favorite"
    }

    override suspend fun favoriteTracks(): List<Track> = failure?.let { throw it } ?: tracks
    override suspend fun favoriteAlbums(): List<Album> = failure?.let { throw it } ?: albums
}
