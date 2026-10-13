package org.jellyfin.mobile.music.downloads

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AccessTokenTest {

    @Test
    fun `adds the current token to a song url`() {
        withAccessToken("http://vault/Audio/a/universal?x=1", "t2") shouldBe "http://vault/Audio/a/universal?x=1&api_key=t2"
    }

    @Test
    fun `adds a query when there is none`() {
        withAccessToken("http://vault/Audio/a/stream", "t") shouldBe "http://vault/Audio/a/stream?api_key=t"
    }

    @Test
    fun `replaces a stale token rather than sending two`() {
        withAccessToken("http://vault/Audio/a/universal?api_key=old&x=1", "new") shouldBe
            "http://vault/Audio/a/universal?x=1&api_key=new"
    }

    @Test
    fun `signed out, the url goes as it is`() {
        withAccessToken("http://vault/Audio/a/universal?x=1", null) shouldBe "http://vault/Audio/a/universal?x=1"
    }
}
