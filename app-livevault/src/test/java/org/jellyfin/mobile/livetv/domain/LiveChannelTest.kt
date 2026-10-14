package org.jellyfin.mobile.livetv.domain

import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.livetv.catalog.channel
import org.junit.jupiter.api.Test

class LiveChannelTest {

    @Test
    fun `a provider channel needs the login`() {
        channel("43", "ESPN", tags = listOf("Sports")).usesProviderLogin shouldBe true
    }

    @Test
    fun `a channel tagged Free does not`() {
        channel("5001", "Tubi Movies", tags = listOf("Free")).usesProviderLogin shouldBe false
    }

    @Test
    fun `a channel named FREE| does not`() {
        channel("5001", "FREE| MLB").usesProviderLogin shouldBe false
    }

    @Test
    fun `channel names say whether they need the login`() {
        channelNameUsesProviderLogin("ESPN") shouldBe true
        channelNameUsesProviderLogin("FREE| MLB") shouldBe false
    }
}
