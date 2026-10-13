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
}
