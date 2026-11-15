package org.jellyfin.mobile.library

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.util.UUID

/** [groupItems]: the library's books by author or by series, and the books of one group. */
class GroupItemsTest {

    private fun book(title: String, author: String = "", series: String? = null, seriesIndex: Float? = null) =
        LibraryItem.AudiobookBundle(
            id = UUID.nameUUIDFromBytes(title.toByteArray()),
            displayName = title,
            description = author,
            imageItemId = UUID.nameUUIDFromBytes("cover $title".toByteArray()),
            imageTag = null,
            runTimeTicks = null,
            progressPercent = null,
            trackIds = emptyList(),
            isBundle = false,
            parentFolderId = null,
            series = series,
            seriesIndex = seriesIndex,
            seriesKey = series?.let { "B:$it" },
        )

    private val caste = book("Caste", author = "Isabel Wilkerson")
    private val warmth = book("The Warmth of Other Suns", author = "Isabel Wilkerson")
    private val hp2 = book("HP2", author = "J.K. Rowling", series = "Harry Potter", seriesIndex = 2f)
    private val hp1 = book("HP1", author = "J.K. Rowling", series = "Harry Potter", seriesIndex = 1f)
    private val zinn = book("A People's History", author = "Howard Zinn")
    private val anonymous = book("Beowulf")
    private val library = listOf(caste, warmth, hp2, hp1, zinn, anonymous)

    private fun List<LibraryItem>.names() = map { it.displayName }

    @Test
    fun `not grouped, the books are as they are`() {
        groupItems(library, GroupBy.NONE, open = null) shouldBe library
    }

    @Test
    fun `by author, a group per author by name, the books without one last`() {
        val groups = groupItems(library, GroupBy.AUTHOR, open = null)

        groups.names() shouldBe listOf("Howard Zinn", "Isabel Wilkerson", "J.K. Rowling", "")
        groups.forEach { it.shouldBeInstanceOf<LibraryItem.Group>() }
        (groups[1] as LibraryItem.Group).count shouldBe 2
    }

    @Test
    fun `a group shows the cover of its first book, a series' first in order`() {
        val potter = groupItems(library, GroupBy.SERIES, open = null).single { it.displayName == "Harry Potter" }

        potter.imageItemId shouldBe hp1.imageItemId
    }

    @Test
    fun `by series, a group per series, then the books in none`() {
        groupItems(library, GroupBy.SERIES, open = null).names() shouldBe
            listOf("Harry Potter", "Caste", "The Warmth of Other Suns", "A People's History", "Beowulf")
    }

    @Test
    fun `an author's group opened shows their books`() {
        groupItems(library, GroupBy.AUTHOR, open = "Isabel Wilkerson") shouldBe listOf(caste, warmth)
    }

    @Test
    fun `a series opened shows its books in the series' order`() {
        groupItems(library, GroupBy.SERIES, open = "Harry Potter") shouldBe listOf(hp1, hp2)
    }

    @Test
    fun `groups keep their ids, so a list holds its place`() {
        groupItems(library, GroupBy.AUTHOR, open = null).map { it.id } shouldBe
            groupItems(library.reversed(), GroupBy.AUTHOR, open = null).map { it.id }
    }
}
