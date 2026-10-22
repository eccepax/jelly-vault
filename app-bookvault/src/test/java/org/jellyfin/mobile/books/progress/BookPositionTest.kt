package org.jellyfin.mobile.books.progress

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class BookPositionTest {

    // The server's runtimes, as the test server gives them: 10,000 ticks a PDF page, 1 s an EPUB
    private val threePagePdf = 30_000L
    private val epub = 10_000_000L

    @Test
    fun `a PDF page is its own number of ticks, as the server counts PDF pages`() {
        pageTicks(page = 1, totalPages = 3, runTimeTicks = threePagePdf) shouldBe 10_000L
        pageAt(ticks = 10_000L, runTimeTicks = threePagePdf, totalPages = 3) shouldBe 1
    }

    @Test
    fun `an EPUB chapter is its share of the book`() {
        pageTicks(page = 4, totalPages = 10, runTimeTicks = epub) shouldBe 4_000_000L
    }

    @Test
    fun `every chapter comes back as itself, whatever the chapter count`() {
        for (total in listOf(3, 7, 61, 997)) {
            for (page in 0 until total) {
                pageAt(pageTicks(page, total, epub), epub, total) shouldBe page
            }
        }
    }

    @Test
    fun `a position partway through a chapter is in that chapter`() {
        // As another reader might save it: 45 % of a ten-chapter book
        pageAt(ticks = 4_500_000L, runTimeTicks = epub, totalPages = 10) shouldBe 4
    }

    @Test
    fun `a position never lands outside the book`() {
        pageAt(ticks = epub, runTimeTicks = epub, totalPages = 10) shouldBe 9
    }

    @Test
    fun `the server's page is where it has the reader`() {
        progress(positionTicks = 4_000_000L).page(totalPages = 10) shouldBe 4
    }

    @Test
    fun `a book the server has as played and not resumed opens at its last page`() {
        progress(positionTicks = 0, played = true).page(totalPages = 10) shouldBe 9
    }

    @Test
    fun `a book never started opens at the start`() {
        progress(positionTicks = 0).page(totalPages = 10) shouldBe 0
    }

    @Test
    fun `a position past the end of the book is not believed`() {
        // What BookVault itself used to send: a share of a made-up 100 s, ten times an EPUB's length
        val oldReport = progress(positionTicks = 400_000_000L)

        oldReport.page(totalPages = 10) shouldBe null
        oldReport.knownAt shouldBe null
    }

    @Test
    fun `nothing is known of a book without a runtime`() {
        val noRuntime = progress(positionTicks = 0, runTimeTicks = null)

        noRuntime.page(totalPages = 10) shouldBe null
        noRuntime.knownAt shouldBe null
    }

    @Test
    fun `a usable position is known as of when it was last read`() {
        progress(positionTicks = 4_000_000L).knownAt shouldBe NOON
    }

    private fun progress(positionTicks: Long, played: Boolean = false, runTimeTicks: Long? = epub) =
        ServerBookProgress(positionTicks, runTimeTicks, played, lastPlayed = NOON)

    private companion object {
        val NOON: Instant = Instant.parse("2026-10-21T12:00:00Z")
    }
}
