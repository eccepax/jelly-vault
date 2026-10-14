package org.jellyfin.mobile.livetv.channels

import org.jellyfin.mobile.livetv.domain.LiveChannel

/** What the viewer can do on the channel list. */
data class ChannelsActions(
    val onQueryChange: (String) -> Unit,
    val onTagSelect: (String?) -> Unit,
    val onFavoritesSelect: () -> Unit,
    val onFavoriteToggle: (LiveChannel) -> Unit,
    val onChannelClick: (LiveChannel) -> Unit,
    val onRetry: () -> Unit,
    val onSignOut: () -> Unit,
)
