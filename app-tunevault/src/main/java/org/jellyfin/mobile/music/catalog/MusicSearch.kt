package org.jellyfin.mobile.music.catalog

import org.jellyfin.mobile.music.domain.Album
import org.jellyfin.mobile.music.domain.Artist
import org.jellyfin.mobile.music.domain.Track

data class SearchResults(
    val artists: List<Artist>,
    val albums: List<Album>,
    val tracks: List<Track>,
) {
    val isEmpty: Boolean get() = artists.isEmpty() && albums.isEmpty() && tracks.isEmpty()

    companion object {
        val EMPTY = SearchResults(emptyList(), emptyList(), emptyList())
    }
}

/** Finds music by name. Separate from [MusicCatalog], which only browses. */
interface MusicSearch {
    /** Artists, albums and songs whose names contain [query] (case-insensitive). */
    suspend fun search(query: String): SearchResults
}
