package org.jellyfin.mobile.music.domain

import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.music.catalog.album
import org.junit.jupiter.api.Test
import java.time.Instant

class AlbumSortTest {

    private fun titles(albums: List<Album>) = albums.map { it.title }

    @Test
    fun `sort keys ignore case, leading articles and punctuation`() {
        sortKey("The Purple Bird") shouldBe "purple bird"
        sortKey("\"Weird Al\" Yankovic") shouldBe "weird al\" yankovic"
        sortKey("A Day") shouldBe "day"
        sortKey("An Hour") shouldBe "hour"
        sortKey("Them Crooked Vultures") shouldBe "them crooked vultures"
        sortKey("The") shouldBe "the"
    }

    @Test
    fun `by title uses sort keys`() {
        val albums = listOf(
            album("1", "Wolf of the Cosmos"),
            album("2", "The Purple Bird"),
            album("3", "\"Weird Al\" Yankovic"),
        )

        titles(AlbumSort.TITLE.sort(albums)) shouldBe listOf("The Purple Bird", "\"Weird Al\" Yankovic", "Wolf of the Cosmos")
    }

    @Test
    fun `by artist groups an artist's albums oldest first, unknown artists last`() {
        val albums = listOf(
            album("1", "Damn", artist = "Kendrick Lamar", year = 2017),
            album("2", "1992 - The Food Album", artist = "", year = null),
            album("3", "Section.80", artist = "Kendrick Lamar", year = 2011),
            album("4", "Homework", artist = "Daft Punk", year = 1997),
        )

        titles(AlbumSort.ARTIST.sort(albums)) shouldBe listOf("Homework", "Section.80", "Damn", "1992 - The Food Album")
    }

    @Test
    fun `by year is newest first, undated last, ties by title`() {
        val albums = listOf(
            album("1", "Homework", year = 1997),
            album("2", "Undated", year = null),
            album("3", "Damn", year = 2017),
            album("4", "Alpha", year = 2017),
        )

        titles(AlbumSort.YEAR.sort(albums)) shouldBe listOf("Alpha", "Damn", "Homework", "Undated")
    }

    @Test
    fun `recently added is newest first`() {
        val albums = listOf(
            album("1", "Old").copy(dateAdded = Instant.parse("2026-01-01T00:00:00Z")),
            album("2", "Unknown").copy(dateAdded = null),
            album("3", "New").copy(dateAdded = Instant.parse("2026-10-04T00:00:00Z")),
        )

        titles(AlbumSort.RECENTLY_ADDED.sort(albums)) shouldBe listOf("New", "Old", "Unknown")
    }
}
