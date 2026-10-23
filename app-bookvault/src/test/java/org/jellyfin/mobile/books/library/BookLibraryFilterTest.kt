package org.jellyfin.mobile.books.library

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.books.data.entity.BookProgressEntity
import org.jellyfin.mobile.common.server.toServerInstant
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

/** Tests for [filterAndSortBooks]. */
class BookLibraryFilterTest {

    private fun book(
        name: String? = "Book",
        tags: List<String>? = null,
        albumArtist: String? = null,
        dateCreated: LocalDateTime? = null,
    ): BaseItemDto = BaseItemDto(
        id = UUID.randomUUID(),
        type = BaseItemKind.BOOK,
        name = name,
        tags = tags,
        albumArtist = albumArtist,
        dateCreated = dateCreated,
    )

    /** A book as the server sends it: read elsewhere at [lastPlayed], an EPUB (1 s long). */
    private fun readOnServer(positionTicks: Long, played: Boolean = false, lastPlayed: LocalDateTime = NOON) =
        book().copy(
            runTimeTicks = EPUB,
            userData = UserItemDataDto(
                playbackPositionTicks = positionTicks,
                playCount = 0,
                isFavorite = false,
                played = played,
                lastPlayedDate = lastPlayed,
                key = "k",
                itemId = UUID.randomUUID(),
            ),
        )

    private fun progress(bookItemId: String, pageNumber: Int, totalPages: Int) =
        BookProgressEntity(bookItemId = bookItemId, pageNumber = pageNumber, totalPages = totalPages, lastUpdated = 0L)

    private fun filter(
        books: List<BaseItemDto>,
        progressByItemId: Map<String, BookProgressEntity> = emptyMap(),
        sort: BookSortOption = BookSortOption.TITLE,
        progressFilter: ProgressFilter = ProgressFilter.ALL,
        tag: String? = null,
    ) = filterAndSortBooks(books, progressByItemId, sort, progressFilter, tag)

    // --- progress filter -----------------------------------------------------------------------

    @Test
    fun `UNREAD includes books with no progress row`() {
        val b = book()
        filter(listOf(b), progressFilter = ProgressFilter.UNREAD) shouldContainExactly listOf(b)
    }

    @Test
    fun `UNREAD includes books at page 0`() {
        val b = book()
        val progress =
            mapOf(requireNotNull(b.id).toString() to progress(b.id.toString(), pageNumber = 0, totalPages = 10))
        filter(listOf(b), progress, progressFilter = ProgressFilter.UNREAD) shouldContainExactly listOf(b)
    }

    @Test
    fun `UNREAD excludes a book with any real progress`() {
        val b = book()
        val progress =
            mapOf(requireNotNull(b.id).toString() to progress(b.id.toString(), pageNumber = 3, totalPages = 10))
        filter(listOf(b), progress, progressFilter = ProgressFilter.UNREAD) shouldBe emptyList()
    }

    @Test
    fun `IN_PROGRESS requires strictly between the first and last page`() {
        val b = book()
        val progress =
            mapOf(requireNotNull(b.id).toString() to progress(b.id.toString(), pageNumber = 5, totalPages = 10))
        filter(listOf(b), progress, progressFilter = ProgressFilter.IN_PROGRESS) shouldContainExactly listOf(b)
    }

    @Test
    fun `a single-page book can never be IN_PROGRESS`() {
        val b = book()
        val progress =
            mapOf(requireNotNull(b.id).toString() to progress(b.id.toString(), pageNumber = 0, totalPages = 1))
        filter(listOf(b), progress, progressFilter = ProgressFilter.IN_PROGRESS) shouldBe emptyList()
    }

    @Test
    fun `a progress row with pageNumber 0 is UNREAD even if totalPages is also 0`() {
        // totalPages == 0 happens when a progress row is inserted before the book finishes
        // loading; combined with pageNumber == 0 it's indistinguishable from "never opened".
        val b = book()
        val progress =
            mapOf(requireNotNull(b.id).toString() to progress(b.id.toString(), pageNumber = 0, totalPages = 0))
        filter(listOf(b), progress, progressFilter = ProgressFilter.UNREAD) shouldContainExactly listOf(b)
    }

    @Test
    fun `a place in a book whose length isn't known yet counts as UNREAD`() {
        val b = book()
        val progress =
            mapOf(requireNotNull(b.id).toString() to progress(b.id.toString(), pageNumber = 5, totalPages = 0))
        filter(listOf(b), progress, progressFilter = ProgressFilter.UNREAD) shouldContainExactly listOf(b)
        filter(listOf(b), progress, progressFilter = ProgressFilter.IN_PROGRESS) shouldBe emptyList()
        filter(listOf(b), progress, progressFilter = ProgressFilter.FINISHED) shouldBe emptyList()
        filter(listOf(b), progress, progressFilter = ProgressFilter.ALL) shouldContainExactly listOf(b)
    }

    @Test
    fun `FINISHED at the exact last page boundary`() {
        val b = book()
        val progress =
            mapOf(requireNotNull(b.id).toString() to progress(b.id.toString(), pageNumber = 9, totalPages = 10))
        filter(listOf(b), progress, progressFilter = ProgressFilter.FINISHED) shouldContainExactly listOf(b)
        filter(listOf(b), progress, progressFilter = ProgressFilter.IN_PROGRESS) shouldBe emptyList()
    }

    @Test
    fun `a one-page book opened is UNREAD, not FINISHED`() {
        val b = book()
        val progress =
            mapOf(requireNotNull(b.id).toString() to progress(b.id.toString(), pageNumber = 0, totalPages = 1))
        filter(listOf(b), progress, progressFilter = ProgressFilter.UNREAD) shouldContainExactly listOf(b)
        filter(listOf(b), progress, progressFilter = ProgressFilter.FINISHED) shouldBe emptyList()
    }

    @Test
    fun `ALL includes every book regardless of progress`() {
        val unread = book(name = "Unread")
        val finished = book(name = "Finished")
        val progress =
            mapOf(
                requireNotNull(finished.id).toString() to progress(finished.id.toString(), pageNumber = 9, totalPages = 10),
            )
        filter(listOf(unread, finished), progress, progressFilter = ProgressFilter.ALL, sort = BookSortOption.TITLE)
            .shouldContainExactly(listOf(finished, unread))
    }

    // --- progress from other devices -----------------------------------------------------------

    @Test
    fun `a book read only on another device is IN_PROGRESS here too`() {
        val b = readOnServer(positionTicks = 4_000_000L)
        filter(listOf(b), progressFilter = ProgressFilter.IN_PROGRESS) shouldContainExactly listOf(b)
        filter(listOf(b), progressFilter = ProgressFilter.UNREAD) shouldContainExactly emptyList()
    }

    @Test
    fun `a book finished on another device is FINISHED here too`() {
        val b = readOnServer(positionTicks = 0, played = true)
        filter(listOf(b), progressFilter = ProgressFilter.FINISHED) shouldContainExactly listOf(b)
    }

    @Test
    fun `a book finished on another device at its last page is FINISHED here too`() {
        val b = readOnServer(positionTicks = 9_000_000L, played = true)
        filter(listOf(b), progressFilter = ProgressFilter.FINISHED) shouldContainExactly listOf(b)
        filter(listOf(b), progressFilter = ProgressFilter.IN_PROGRESS) shouldBe emptyList()
    }

    @Test
    fun `a book read again from the start on another device is UNREAD here too`() {
        val b = readOnServer(positionTicks = 0, played = false)
        val finishedHere = mapOf(b.id.toString() to progress(b.id.toString(), 9, 10))
        filter(listOf(b), finishedHere, progressFilter = ProgressFilter.UNREAD) shouldContainExactly listOf(b)
    }

    @Test
    fun `reading here more recently wins over another device`() {
        val b = readOnServer(positionTicks = 4_000_000L, lastPlayed = NOON)
        val later = NOON.toServerInstant().toEpochMilli() + 1
        val here = mapOf(b.id.toString() to progress(b.id.toString(), 9, 10).copy(lastUpdated = later))
        filter(listOf(b), here, progressFilter = ProgressFilter.FINISHED) shouldContainExactly listOf(b)
    }

    @Test
    fun `reading on another device more recently wins over here`() {
        val b = readOnServer(positionTicks = 4_000_000L, lastPlayed = NOON)
        val here = mapOf(b.id.toString() to progress(b.id.toString(), 0, 10))
        filter(listOf(b), here, progressFilter = ProgressFilter.IN_PROGRESS) shouldContainExactly listOf(b)
    }

    @Test
    fun `a position BookVault used to send says nothing about a book`() {
        val b = readOnServer(positionTicks = 400_000_000L)
        filter(listOf(b), progressFilter = ProgressFilter.UNREAD) shouldContainExactly listOf(b)
    }

    // --- tag filter ------------------------------------------------------------------------------

    @Test
    fun `tag filter with null tag passes everything`() {
        val b = book(tags = null)
        filter(listOf(b), tag = null) shouldContainExactly listOf(b)
    }

    @Test
    fun `tag filter excludes a book with null tags when a tag is requested`() {
        val b = book(tags = null)
        filter(listOf(b), tag = "fiction") shouldBe emptyList()
    }

    @Test
    fun `tag filter excludes a book without the matching tag`() {
        val b = book(tags = listOf("nonfiction"))
        filter(listOf(b), tag = "fiction") shouldBe emptyList()
    }

    @Test
    fun `tag filter includes a book with the matching tag`() {
        val b = book(tags = listOf("fiction", "classic"))
        filter(listOf(b), tag = "fiction") shouldContainExactly listOf(b)
    }

    // --- sort --------------------------------------------------------------------------------

    @Test
    fun `TITLE and AUTHOR sorts are case-insensitive and sort null first`() {
        val named = book(name = "banana")
        val unnamed = book(name = null)
        filter(listOf(named, unnamed), sort = BookSortOption.TITLE) shouldContainExactly listOf(unnamed, named)
    }

    @Test
    fun `DATE_ADDED sorts descending and puts nulls last`() {
        val early = book(name = "Early", dateCreated = LocalDateTime.of(2020, 1, 1, 0, 0))
        val late = book(name = "Late", dateCreated = LocalDateTime.of(2021, 1, 1, 0, 0))
        val noDate = book(name = "NoDate", dateCreated = null)
        filter(listOf(early, late, noDate), sort = BookSortOption.DATE_ADDED) shouldContainExactly listOf(late, early, noDate)
    }

    // --- availableTags -------------------------------------------------------------------------

    @Test
    fun `availableTags collapses duplicates across books`() {
        val books = listOf(book(tags = listOf("fiction")), book(tags = listOf("fiction", "classic")))
        availableTags(books) shouldContainExactly listOf("classic", "fiction")
    }

    @Test
    fun `availableTags tolerates a null tags list on some books`() {
        val books = listOf(book(tags = null), book(tags = listOf("fiction")))
        availableTags(books) shouldContainExactly listOf("fiction")
    }

    @Test
    fun `availableTags result is sorted`() {
        val books = listOf(book(tags = listOf("zebra", "apple")))
        availableTags(books) shouldContainExactly listOf("apple", "zebra")
    }

    private companion object {
        const val EPUB = 10_000_000L
        val NOON: LocalDateTime = LocalDateTime.of(2026, 10, 21, 12, 0)
    }
}
