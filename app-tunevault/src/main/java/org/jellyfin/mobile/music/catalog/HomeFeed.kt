package org.jellyfin.mobile.music.catalog

import org.jellyfin.mobile.music.domain.Album
import org.jellyfin.mobile.music.domain.Track

/** What the Home screen shows. Separate from [MusicCatalog], which only browses. */
interface HomeFeed {
    /** Tracks the user has played, most recent first. */
    suspend fun recentlyPlayed(limit: Int): List<Track>

    /** Albums most recently added to the server, newest first. */
    suspend fun recentlyAdded(limit: Int): List<Album>
}
