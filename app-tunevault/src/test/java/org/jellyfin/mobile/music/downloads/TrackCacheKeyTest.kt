package org.jellyfin.mobile.music.downloads

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TrackCacheKeyTest {

    @Test
    fun `a song's stream is known by its id, whatever the quality, server address or token`() {
        val original = "http://vault:8096/Audio/0a1b2c/universal?maxStreamingBitrate=140000000&api_key=old"
        val dataSaver = "http://other:8096/Audio/0a1b2c/universal?maxStreamingBitrate=128000&api_key=new"

        TrackCacheKey.trackIdOf(original) shouldBe "0a1b2c"
        TrackCacheKey.trackIdOf(dataSaver) shouldBe "0a1b2c"
    }

    @Test
    fun `the id is found in any song url`() {
        TrackCacheKey.trackIdOf("http://vault/Audio/abc/stream?static=true") shouldBe "abc"
    }

    @Test
    fun `anything else has no song id`() {
        TrackCacheKey.trackIdOf("http://vault/Items/abc/Images/Primary") shouldBe null
    }
}
