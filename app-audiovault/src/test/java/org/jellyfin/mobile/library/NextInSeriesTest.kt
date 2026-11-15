package org.jellyfin.mobile.library

import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.data.entity.AudiobookEntryEntity
import org.junit.jupiter.api.Test
import java.util.UUID

/** [nextInSeries]: the book after the one a track is in, when that's a book of a series. */
class NextInSeriesTest {

    private val tracks = (1..4).associateWith { UUID.randomUUID() }

    private fun book(n: Int, series: String? = "Harry Potter", index: Float? = n.toFloat()) = AudiobookEntryEntity(
        id = "S:${tracks.getValue(n)}",
        serverId = 1L,
        parentId = null,
        title = "HP$n",
        author = null,
        imageItemId = null,
        imageTag = null,
        runtimeTicks = 100L,
        trackIds = tracks.getValue(n).toString(),
        isBundle = false,
        dateAdded = "",
        lastPlayed = "",
        playedPercentage = 0f,
        series = series,
        seriesIndex = index,
        seriesKey = series?.let { "B:$it" },
    )

    @Test
    fun `the next book is the one after it in the series`() {
        val library = listOf(book(3), book(1), book(2))

        nextInSeries(library, tracks.getValue(1).toString()) shouldBe NextBook("HP2", listOf(tracks.getValue(2)))
        nextInSeries(library, tracks.getValue(2).toString()) shouldBe NextBook("HP3", listOf(tracks.getValue(3)))
    }

    @Test
    fun `the last of a series has none after it`() {
        nextInSeries(listOf(book(1), book(2)), tracks.getValue(2).toString()) shouldBe null
    }

    @Test
    fun `a book in no series has none after it`() {
        nextInSeries(listOf(book(1, series = null), book(2)), tracks.getValue(1).toString()) shouldBe null
    }

    @Test
    fun `a track that's in no book of the library has none after it`() {
        nextInSeries(listOf(book(1), book(2)), UUID.randomUUID().toString()) shouldBe null
    }
}
