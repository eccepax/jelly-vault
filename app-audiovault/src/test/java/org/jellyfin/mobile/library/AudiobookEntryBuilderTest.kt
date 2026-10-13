package org.jellyfin.mobile.library

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

    @Test
    fun `bundle author falls back from albumArtist to first artist`() {
        val items = listOf(
            item(path = "/books/Dir/a.m4b", album = "Book", albumArtist = null, artists = listOf("Narrator One")),
            item(path = "/books/Dir/b.m4b", album = "Book"),
        )
        AudiobookEntryBuilder.build(items, serverId = 1L).single().author shouldBe "Narrator One"
    }
}
