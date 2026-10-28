package org.jellyfin.mobile.player.playback

import io.kotest.matchers.shouldBe
import org.junit.Test

/** A track's url as a cast device fetches it on its own, without the player's auth header. */
class CastUrlTest {

    @Test
    fun `a stream from the server carries the token`() {
        castUrl("https://vault.test/Audio/a/universal?Container=mp3", "https://vault.test", "t") shouldBe
            "https://vault.test/Audio/a/universal?Container=mp3&ApiKey=t"
    }

    @Test
    fun `anything not from the server is left as it is, so the token goes nowhere else`() {
        castUrl("https://elsewhere.test/a.mp3", "https://vault.test", "t") shouldBe "https://elsewhere.test/a.mp3"
        castUrl("file:///data/a.m4b", "https://vault.test", "t") shouldBe "file:///data/a.m4b"
    }

    @Test
    fun `a server url that only starts like the server's isn't the server's`() {
        castUrl("https://vault.test.evil/a.mp3", "https://vault.test", "t") shouldBe "https://vault.test.evil/a.mp3"
    }

    @Test
    fun `signed out, there's no token to add`() {
        castUrl("https://vault.test/Audio/a/universal", null, null) shouldBe "https://vault.test/Audio/a/universal"
    }
}
