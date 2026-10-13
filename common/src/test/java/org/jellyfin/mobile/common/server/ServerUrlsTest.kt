package org.jellyfin.mobile.common.server

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.util.UUID

class ServerUrlsTest {

    private val itemId = UUID.fromString("ded9d452-b0ef-fd8a-13f9-78d5ba92d1b6")

    // --- withAccessToken -------------------------------------------------------------------------

    @Test
    fun `adds the current token to a url`() {
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

    // --- itemDownloadUrl -------------------------------------------------------------------------

    @Test
    fun `builds a dashless item download url carrying the token`() {
        itemDownloadUrl("https://server", itemId, "tok") shouldBe
            "https://server/Items/ded9d452b0effd8a13f978d5ba92d1b6/Download?api_key=tok"
    }

    @Test
    fun `strips a trailing slash from the base url`() {
        itemDownloadUrl("https://server/", itemId, "tok") shouldBe
            "https://server/Items/ded9d452b0effd8a13f978d5ba92d1b6/Download?api_key=tok"
    }

    @Test
    fun `without a token the url never carries the string null`() {
        val url = itemDownloadUrl("https://server", itemId, null)

        url shouldBe "https://server/Items/ded9d452b0effd8a13f978d5ba92d1b6/Download"
        url shouldNotContain "null"
    }
}
