package org.jellyfin.mobile.livetv.channels

/** Compose test tags for [ChannelsScreen]. */
object ChannelsScreenTags {
    const val LOADING = "channels_loading"
    const val SEARCH = "channels_search"
    const val CHIPS = "channels_chips"
    const val CONTINUE = "channels_continue"
    const val CHANNEL_PREFIX = "channel_"

    fun channel(id: String) = "$CHANNEL_PREFIX$id"
}
