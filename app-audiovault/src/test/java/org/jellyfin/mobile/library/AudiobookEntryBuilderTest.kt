package org.jellyfin.mobile.library

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

/**
 * Tests for [AudiobookEntryBuilder]. Resume position and the progress bar both depend on this
 * grouping, which goes by filesystem path because Jellyfin's recursive item query often omits
 * `parentId`.
 */
class AudiobookEntryBuilderTest {

    @Suppress("LongParameterList")
    private fun item(
        name: String = "Track",
        path: String? = "/audiobooks/Some Book/track.m4b",
        parentId: UUID? = null,
        album: String? = "Some Book",
        indexNumber: Int? = null,
        runTimeTicks: Long = 100L,
        dateCreated: LocalDateTime? = null,
        lastPlayedDate: LocalDateTime? = null,
        albumArtist: String? = null,
        artists: List<String>? = null,
    ): BaseItemDto = BaseItemDto(
        id = UUID.randomUUID(),
        type = BaseItemKind.AUDIO_BOOK,
        name = name,
        path = path,
        parentId = parentId,
        album = album,
        indexNumber = indexNumber,
        runTimeTicks = runTimeTicks,
        dateCreated = dateCreated,
        albumArtist = albumArtist,
        artists = artists,
        userData = if (lastPlayedDate != null) {
            UserItemDataDto(
                rating = null,
                playedPercentage = null,
                unplayedItemCount = null,
                playbackPositionTicks = 0L,
                playCount = 0,
                isFavorite = false,
                likes = null,
                lastPlayedDate = lastPlayedDate,
                played = false,
                key = "",
                itemId = UUID.randomUUID(),
            )
        } else {
            null
        },
    )

    @Test
    fun `single item in its directory becomes a standalone entry`() {
        val entries = AudiobookEntryBuilder.build(listOf(item(path = "/books/Solo Book/file.m4b")), serverId = 1L)
        entries.shouldHaveSize(1)
        entries.single().isBundle shouldBe false
    }

    @Test
    fun `multiple files with the same dir and album become one bundle`() {
        val items = listOf(
            item(path = "/books/My Book/ch1.m4b", album = "My Book"),
            item(path = "/books/My Book/ch2.m4b", album = "My Book"),
        )
        val entries = AudiobookEntryBuilder.build(items, serverId = 1L)
        entries.shouldHaveSize(1)
        entries.single().isBundle shouldBe true
        entries.single().trackIds.split(",").shouldHaveSize(2)
    }

    @Test
    fun `same dir but different album tags produce separate standalone entries`() {
        val items = listOf(
            item(path = "/books/Anthology/story1.m4b", album = "Story One"),
            item(path = "/books/Anthology/story2.m4b", album = "Story Two"),
        )
        val entries = AudiobookEntryBuilder.build(items, serverId = 1L)
        entries.shouldHaveSize(2)
        entries.forEach { it.isBundle shouldBe false }
    }

    @Test
    fun `null path falls back to grouping by parentId`() {
        val sharedParent = UUID.randomUUID()
        val items = listOf(
            item(path = null, parentId = sharedParent, album = "Book"),
            item(path = null, parentId = sharedParent, album = "Book"),
        )
        val entries = AudiobookEntryBuilder.build(items, serverId = 1L)
        entries.shouldHaveSize(1)
        entries.single().isBundle shouldBe true
    }

    @Test
    fun `null path and null parentId both fall back to a shared root bucket`() {
        val items = listOf(
            item(path = null, parentId = null, album = "Book"),
            item(path = null, parentId = null, album = "Book"),
        )
        val entries = AudiobookEntryBuilder.build(items, serverId = 1L)
        entries.shouldHaveSize(1)
        entries.single().isBundle shouldBe true
    }

    @Test
    fun `path separators are normalized between forward and back slashes`() {
        val items = listOf(
            item(path = "C:\\books\\Same Book\\ch1.m4b", album = "Same Book"),
            item(path = "C:/books/Same Book/ch2.m4b", album = "Same Book"),
        )
        val entries = AudiobookEntryBuilder.build(items, serverId = 1L)
        entries.shouldHaveSize(1)
        entries.single().isBundle shouldBe true
    }

    @Test
    fun `blank and null album tags group together within the same directory`() {
        val items = listOf(
            item(path = "/books/Dir/a.m4b", album = null),
            item(path = "/books/Dir/b.m4b", album = "  "),
        )
        val entries = AudiobookEntryBuilder.build(items, serverId = 1L)
        entries.shouldHaveSize(1)
        entries.single().isBundle shouldBe true
    }

    @Test
    fun `duplicate album tags in different directories stay separate bundles`() {
        val items = listOf(
            item(path = "/books/DirA/ch1.m4b", album = "Common Title"),
            item(path = "/books/DirA/ch2.m4b", album = "Common Title"),
            item(path = "/books/DirB/ch1.m4b", album = "Common Title"),
            item(path = "/books/DirB/ch2.m4b", album = "Common Title"),
        )
        val entries = AudiobookEntryBuilder.build(items, serverId = 1L)
        entries.shouldHaveSize(2)
        entries.forEach { it.isBundle shouldBe true }
    }

    @Test
    fun `bundle title prefers the album tag over the folder name`() {
        val items = listOf(
            item(path = "/books/folder-name/ch1.m4b", album = "Real Title"),
            item(path = "/books/folder-name/ch2.m4b", album = "Real Title"),
        )
        AudiobookEntryBuilder.build(items, serverId = 1L).single().title shouldBe "Real Title"
    }

    @Test
    fun `bundle title falls back to the folder name when the album tag is blank`() {
        val items = listOf(
            item(path = "/books/Folder Name/ch1.m4b", album = ""),
            item(path = "/books/Folder Name/ch2.m4b", album = ""),
        )
        AudiobookEntryBuilder.build(items, serverId = 1L).single().title shouldBe "Folder Name"
    }

    @Test
    fun `bundle trackIds are ordered by indexNumber then name`() {
        val a = item(path = "/books/Dir/z.m4b", album = "Book", indexNumber = 2, name = "Zed")
        val b = item(path = "/books/Dir/a.m4b", album = "Book", indexNumber = 1, name = "Alpha")
        val c = item(path = "/books/Dir/m.m4b", album = "Book", indexNumber = 2, name = "Middle")
        val entries = AudiobookEntryBuilder.build(listOf(a, b, c), serverId = 1L)
        val orderedIds = entries.single().trackIds.split(",")
        orderedIds shouldBe listOf(b.id, c.id, a.id).map { it.toString() }
    }

    @Test
    fun `bundle dateAdded and lastPlayed aggregate with min-max across tracks`() {
        val early = item(path = "/books/Dir/a.m4b", album = "Book", dateCreated = LocalDateTime.of(2020, 1, 1, 0, 0))
        val late = item(path = "/books/Dir/b.m4b", album = "Book", dateCreated = LocalDateTime.of(2021, 1, 1, 0, 0))
        val entries = AudiobookEntryBuilder.build(listOf(early, late), serverId = 1L)
        entries.single().dateAdded shouldBe early.dateCreated.toString()
    }

    // --- Author: the writer -----------------------------------------------------------------------

    @Test
    fun `a book's author is its writer (the artist), not the narrator (the album artist)`() {
        val solo = item(
            path = "/books/Caste/caste.m4b",
            album = "Caste",
            albumArtist = "Robin Miles",
            artists = listOf("Isabel Wilkerson"),
        )
        val parts = listOf(
            item(
                path = "/books/Zinn/1.m4b",
                album = "History",
                albumArtist = "Narrator",
                artists = listOf("Howard Zinn"),
            ),
            item(
                path = "/books/Zinn/2.m4b",
                album = "History",
                albumArtist = "Narrator",
                artists = listOf("Howard Zinn"),
            ),
        )

        AudiobookEntryBuilder.build(listOf(solo) + parts, serverId = 1L).map { it.author } shouldContainExactlyInAnyOrder
            listOf("Isabel Wilkerson", "Howard Zinn")
    }

    @Test
    fun `without an artist the author falls back to the album artist`() {
        val items = listOf(
            item(path = "/books/Dir/a.m4b", album = "Book", albumArtist = "Someone"),
            item(path = "/books/Dir/b.m4b", album = "Book", albumArtist = "Someone"),
        )
        AudiobookEntryBuilder.build(items, serverId = 1L).single().author shouldBe "Someone"
    }

    // --- Disc folders ----------------------------------------------------------------------------

    @Test
    fun `a book in disc folders, each with its own album tag, is one book named without the disc`() {
        val items = listOf(
            item(
                name = "D2 T1",
                path = "/books/Stevenson/Just Mercy (Disc 2)/01.mp3",
                album = "Just Mercy (Disc 2)",
                indexNumber = 1,
            ),
            item(
                name = "D1 T2",
                path = "/books/Stevenson/Just Mercy (Disc 1)/02.mp3",
                album = "Just Mercy (Disc 1)",
                indexNumber = 2,
            ),
            item(
                name = "D1 T1",
                path = "/books/Stevenson/Just Mercy (Disc 1)/01.mp3",
                album = "Just Mercy (Disc 1)",
                indexNumber = 1,
            ),
            item(
                name = "D10 T1",
                path = "/books/Stevenson/Just Mercy (Disc 10)/01.mp3",
                album = "Just Mercy (Disc 10)",
                indexNumber = 1,
            ),
        )

        val book = AudiobookEntryBuilder.build(items, serverId = 1L).single()

        book.title shouldBe "Just Mercy"
        // Disc by disc, by number (10 after 2), then track by track
        book.trackIds.split(",") shouldBe listOf("D1 T1", "D1 T2", "D2 T1", "D10 T1").map { name ->
            items.first {
                it.name == name
            }.id.toString()
        }
    }

    @Test
    fun `disc folders are recognised however they're written`() {
        listOf(
            "Book - CD 1" to "Book - CD 2",
            "Book Disc1" to "Book Disc2",
            "Book [Disk 1]" to "Book [Disk 2]",
        ).forEach { (one, two) ->
            val items = listOf(
                item(path = "/books/$one/01.mp3", album = one),
                item(path = "/books/$two/01.mp3", album = two),
            )
            AudiobookEntryBuilder.build(items, serverId = 1L).map { it.title } shouldBe listOf("Book")
        }
    }

    @Test
    fun `books in different folders that only share a name stay apart`() {
        val items = listOf(
            item(path = "/books/A/Book (Disc 1)/01.mp3", album = "Book (Disc 1)"),
            item(path = "/books/B/Book (Disc 2)/01.mp3", album = "Book (Disc 2)"),
        )
        AudiobookEntryBuilder.build(items, serverId = 1L) shouldHaveSize 2
    }

    // --- Shown as separate books (a series) ------------------------------------------------------

    private val potter = listOf(3, 1, 2).map { n ->
        item(
            name = "HP$n",
            path = "/books/Rowling/HP$n.m4b",
            album = "Harry Potter (Stephen Fry)",
            indexNumber = n,
            artists = listOf("J.K. Rowling"),
        )
    }

    @Test
    fun `a book of several files is shown as separate books once asked, a series in the files' order`() {
        val key = AudiobookEntryBuilder.build(potter, serverId = 1L).single().id

        val books = AudiobookEntryBuilder.build(potter, serverId = 1L, separateBooks = setOf(key))

        books.sortedBy { it.seriesIndex }.map { it.title } shouldBe listOf("HP1", "HP2", "HP3")
        books.map { it.series }.distinct() shouldBe listOf("Harry Potter (Stephen Fry)")
        books.map { it.seriesKey }.distinct() shouldBe listOf(key)
        books.map { it.author }.distinct() shouldBe listOf("J.K. Rowling")
        books.forEach { it.isBundle shouldBe false }
    }

    @Test
    fun `books not asked to be separate stay one book, with no series`() {
        val book = AudiobookEntryBuilder.build(
            potter,
            serverId = 1L,
            separateBooks = setOf("B:something else"),
        ).single()

        book.series shouldBe null
        book.seriesKey shouldBe null
    }
}
