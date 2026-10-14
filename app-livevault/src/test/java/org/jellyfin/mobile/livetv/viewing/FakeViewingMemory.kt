package org.jellyfin.mobile.livetv.viewing

import kotlinx.coroutines.flow.MutableStateFlow

class FakeViewingMemory(
    override var tag: String? = null,
    override var favoritesOnly: Boolean = false,
    lastChannel: String? = null,
) : ViewingMemory {
    override val lastChannelId = MutableStateFlow(lastChannel)

    override fun rememberChannel(channelId: String) {
        lastChannelId.value = channelId
    }

    override fun rememberFilter(tag: String?, favoritesOnly: Boolean) {
        this.tag = tag
        this.favoritesOnly = favoritesOnly
    }

    override fun forget() {
        tag = null
        favoritesOnly = false
        lastChannelId.value = null
    }
}
