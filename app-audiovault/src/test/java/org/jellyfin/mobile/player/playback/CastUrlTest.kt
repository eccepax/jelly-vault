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
    fun `the server's url written another way is still the server's`() {
        castUrl("https://Vault.Test/Audio/a", "HTTPS://vault.test/", "t") shouldBe "https://Vault.Test/Audio/a?ApiKey=t"
        castUrl("https://vault.test:443/Audio/a", "https://vault.test", "t") shouldBe "https://vault.test:443/Audio/a?ApiKey=t"
        castUrl("http://vault.test/Audio/a", "http://vault.test:80", "t") shouldBe "http://vault.test/Audio/a?ApiKey=t"
    }

    @Test
    fun `a server under a path is the server only under it, and only at its own scheme and port`() {
        castUrl("https://vault.test/jellyfin/Audio/a", "https://vault.test/jellyfin", "t") shouldBe
            "https://vault.test/jellyfin/Audio/a?ApiKey=t"
        castUrl("https://vault.test/other/a", "https://vault.test/jellyfin", "t") shouldBe "https://vault.test/other/a"
        castUrl("https://vault.test/jellyfinx/a", "https://vault.test/jellyfin", "t") shouldBe "https://vault.test/jellyfinx/a"
        castUrl("http://vault.test/Audio/a", "https://vault.test", "t") shouldBe "http://vault.test/Audio/a"
        castUrl("https://vault.test:8443/Audio/a", "https://vault.test", "t") shouldBe "https://vault.test:8443/Audio/a"
    }

    @Test
    fun `a server named as Docker and LAN names often are, with an underscore, still gets the token`() {
        castUrl("http://jellyfin_server:8096/Audio/a", "http://jellyfin_server:8096", "t") shouldBe
            "http://jellyfin_server:8096/Audio/a?ApiKey=t"
        castUrl("http://Jellyfin_Server/Audio/a", "http://jellyfin_server:80", "t") shouldBe
            "http://Jellyfin_Server/Audio/a?ApiKey=t"
        castUrl("http://other_host:8096/Audio/a", "http://jellyfin_server:8096", "t") shouldBe
            "http://other_host:8096/Audio/a"
    }

    @Test
    fun `signed out, there's no token to add`() {
        castUrl("https://vault.test/Audio/a/universal", null, null) shouldBe "https://vault.test/Audio/a/universal"
    }
}
