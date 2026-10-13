package org.jellyfin.mobile.music.catalog

import kotlinx.coroutines.CompletableDeferred
import org.jellyfin.mobile.music.domain.Album
import org.jellyfin.mobile.music.domain.Track

class FakeHomeFeed(
    var played: List<Track> = emptyList(),
    var added: List<Album> = emptyList(),
) : HomeFeed {
    var failure: Throwable? = null
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun recentlyPlayed(limit: Int): List<Track> {
        gate?.await()
        failure?.let { throw it }
        return played.take(limit)
    }

    override suspend fun recentlyAdded(limit: Int): List<Album> {
        gate?.await()
        failure?.let { throw it }
        return added.take(limit)
    }
}
