package org.jellyfin.mobile.common.downloads

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import org.junit.jupiter.api.Test

/** Names come from the server, so they must never escape the downloads folder or collide. */
class DownloadFileNameTest {

    private val safe = Regex("[A-Za-z0-9_-][A-Za-z0-9._-]*")

    @Test
    fun `a plain item id is used as it is`() {
        downloadFileName("4f1c2b7e9d8a4c3b8e2f1a0b9c8d7e6f") shouldBe "4f1c2b7e9d8a4c3b8e2f1a0b9c8d7e6f"
    }

    @Test
    fun `path separators and parent references can't escape the folder`() {
        listOf("../../shared_prefs/x", "a/b", "a\\b", "..", ".", "", ".hidden").forEach { name ->
            downloadFileName(name) shouldMatch safe
        }
    }

    @Test
    fun `different unsafe names never share a file`() {
        downloadFileName("a/b") shouldNotBe downloadFileName("a_b")
        downloadFileName("a/b") shouldNotBe downloadFileName("a\\b")
    }

    @Test
    fun `the same name always gives the same file`() {
        downloadFileName("a/b") shouldBe downloadFileName("a/b")
    }
}
