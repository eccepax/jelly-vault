package org.jellyfin.mobile.livetv.catalog

import org.jellyfin.mobile.livetv.domain.LiveChannel

/**
 * Everything LiveVault reads from the server about channels. The only implementation that touches
 * the Jellyfin SDK is [JellyfinLiveTvCatalog]; everything above this layer is tested against a fake.
 */
interface LiveTvCatalog {
    /** Every channel, in channel number order, with what's on now. */
    suspend fun channels(): List<LiveChannel>

    /** Saves the channel's favorite flag on the server, so every Jellyfin client agrees. */
    suspend fun setFavorite(channelId: String, favorite: Boolean)
}
