package org.jellyfin.mobile.livetv.catalog

import kotlinx.coroutines.CompletableDeferred
import org.jellyfin.mobile.livetv.domain.LiveChannel
import org.jellyfin.mobile.livetv.domain.LiveProgram
import java.time.Instant

class FakeLiveTvCatalog : LiveTvCatalog {
    var channels: List<LiveChannel> = emptyList()

    /** Thrown by every call while set. */
    var failure: Exception? = null

    /** When set, calls wait for it to complete before answering. */
    var gate: CompletableDeferred<Unit>? = null

    /** How many times channels() has answered. */
    var channelLoads = 0
        private set

    /** Every setFavorite call, in order. */
    val favoriteCalls = mutableListOf<Pair<String, Boolean>>()

    override suspend fun channels(): List<LiveChannel> {
        gate?.await()
        failure?.let { throw it }
        channelLoads++
        return channels
    }

    override suspend fun setFavorite(channelId: String, favorite: Boolean) {
        gate?.await()
        favoriteCalls += channelId to favorite
        failure?.let { throw it }
        channels = channels.map { if (it.id == channelId) it.copy(isFavorite = favorite) else it }
    }
}

fun channel(
    number: String,
    name: String = "Channel $number",
    tags: List<String> = emptyList(),
    favorite: Boolean = false,
    now: LiveProgram? = null,
    logoUrl: String? = null,
) = LiveChannel(
    id = "id-$number",
    number = number,
    name = name,
    logoUrl = logoUrl,
    tags = tags,
    isFavorite = favorite,
    nowPlaying = now,
)

fun program(title: String, start: String = "2026-10-14T18:00:00Z", end: String = "2026-10-14T20:00:00Z") =
    LiveProgram(title, Instant.parse(start), Instant.parse(end))
