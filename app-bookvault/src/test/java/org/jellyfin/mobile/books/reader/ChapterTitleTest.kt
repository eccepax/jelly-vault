package org.jellyfin.mobile.books.reader

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ChapterTitleTest {

    private val toc = listOf(
        EpubTocEntry("Contents", 4),
        EpubTocEntry("Introduction", 6),
        EpubTocEntry("1. The Laboratory", 7),
    )

    @Test
    fun `a chapter listed in the table of contents gets its own title`() {
        chapterTitleAt(toc, 6) shouldBe "Introduction"
    }

    @Test
    fun `a spine item between entries belongs to the entry before it`() {
        chapterTitleAt(toc, 5) shouldBe "Contents"
        chapterTitleAt(toc, 12) shouldBe "1. The Laboratory"
    }

    @Test
    fun `front matter before the first entry, like a cover, has no title`() {
        chapterTitleAt(toc, 0).shouldBeNull()
    }

    @Test
    fun `the order of the table of contents doesn't matter`() {
        chapterTitleAt(toc.reversed(), 6) shouldBe "Introduction"
    }

    @Test
    fun `an empty table of contents gives no title`() {
        chapterTitleAt(emptyList(), 3).shouldBeNull()
    }

    // --- several chapters in one file ------------------------------------------------------------

    private val shared = listOf(
        EpubTocEntry("Chapter I", 1, "ch1"),
        EpubTocEntry("Chapter II", 2, "ch2"),
        EpubTocEntry("Chapter III", 2, "ch3"),
        EpubTocEntry("Chapter IV", 2, "ch4"),
        EpubTocEntry("Chapter V", 3, "ch5"),
    )

    @Test
    fun `a file holding several chapters is read from its first`() {
        tocEntryAt(shared, spineIndex = 2, chosen = null) shouldBe 1
        chapterTitleAt(shared, 2) shouldBe "Chapter II"
    }

    @Test
    fun `a chapter chosen from the contents is the one being read, in a file it shares`() {
        tocEntryAt(shared, spineIndex = 2, chosen = shared[2]) shouldBe 2
    }

    @Test
    fun `a chapter chosen once is forgotten on reaching another file`() {
        tocEntryAt(shared, spineIndex = 3, chosen = shared[2]) shouldBe 4
    }

    @Test
    fun `a file between entries is read as part of the entry before it`() {
        tocEntryAt(toc, spineIndex = 12, chosen = null) shouldBe 2
        tocEntryAt(toc, spineIndex = 0, chosen = null) shouldBe -1
    }
}
