package org.jellyfin.mobile.player.ui

import org.jellyfin.mobile.utils.Constants

data class AudiobookDisplayPreferences(
    val skipBackLength: Long = Constants.AUDIOBOOK_SKIP_BACK_MS,
    val skipForwardLength: Long = Constants.AUDIOBOOK_SKIP_FORWARD_MS,
)
