package org.jellyfin.mobile.livetv.playback

import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Test

class LiveTvSessionsTest {

    private val roku = ChannelSession(deviceName = "Living Room", userName = "root", channelName = "ESPN")
    private val tablet = ChannelSession(deviceName = "Tablet", userName = "kid", channelName = "FREE| Cartoons")

    @Test
    fun `only viewers of provider channels hold the login`() {
        listOf(roku, tablet).holdingLogin() shouldContainExactly listOf(roku)
    }
}
