package org.jellyfin.mobile.downloads

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.jupiter.api.Test
import java.util.UUID

class DownloadedBookTest {

    private val chapter1 = track("Chapter 1", index = 1, path = "/books/Dune/01.mp3", album = "Dune")
    private val chapter2 = track("Chapter 2", index = 2, path = "/books/Dune/02.mp3", album = "Dune")
    private val chapter3 = track("Chapter 3", index = 3, path = "/books/Dune/03.mp3", album = "Dune")

    @Test
    fun `a track's book is the downloaded tracks from its folder and album, in track order`() {
        val downloads = listOf(chapter3, chapter1, chapter2)

        downloadedBookOf(chapter2, downloads) shouldBe listOf(chapter1, chapter2, chapter3)
    }

    @Test
    fun `tracks without numbers are ordered by name`() {
        val b = track("B", index = null, path = "/books/Essays/b.mp3", album = "Essays")
        val a = track("A", index = null, path = "/books/Essays/a.mp3", album = "Essays")

        downloadedBookOf(b, listOf(b, a)) shouldBe listOf(a, b)
    }

    @Test
    fun `another book in the same folder isn't part of it`() {
        val otherBook = track("Other", index = 1, path = "/books/Dune/other.mp3", album = "Children of Dune")

        downloadedBookOf(chapter1, listOf(chapter1, otherBook, chapter2)) shouldBe listOf(chapter1, chapter2)
    }

    @Test
    fun `the same album in another folder isn't part of it`() {
        val elsewhere = track("Chapter 1", index = 1, path = "/other/Dune/01.mp3", album = "Dune")

        downloadedBookOf(chapter1, listOf(chapter1, elsewhere)) shouldBe listOf(chapter1)
    }

    @Test
    fun `a track the server told nothing about is a book of its own`() {
        val unknown = DownloadEntity("unknown", source(UUID.randomUUID(), item = null))

        downloadedBookOf(unknown, listOf(unknown, chapter1)) shouldBe listOf(unknown)
    }

    private fun track(name: String, index: Int?, path: String, album: String): DownloadEntity {
        val id = UUID.randomUUID()
        val item = BaseItemDto(
            id = id,
            type = BaseItemKind.AUDIO,
            name = name,
            indexNumber = index,
            path = path,
            album = album,
        )
        return DownloadEntity(id.toString(), source(id, item))
    }

    private fun source(id: UUID, item: BaseItemDto?) = mockk<LocalJellyfinMediaSource>(relaxed = true) {
        every { itemId } returns id
        every { this@mockk.item } returns item
    }
}
