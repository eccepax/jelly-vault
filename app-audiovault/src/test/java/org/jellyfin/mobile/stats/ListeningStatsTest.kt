package org.jellyfin.mobile.stats

import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.data.entity.FinishedBookEntity
import org.jellyfin.mobile.data.entity.ListeningDayEntity
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** [listeningStatsOf]: today's listening, the last seven days', all of it, and the books finished. */
class ListeningStatsTest {

    private val today = LocalDate.of(2026, 11, 15)
    private val hour = 3_600_000L

    private fun day(daysAgo: Long, ms: Long) = ListeningDayEntity(today.minusDays(daysAgo).toString(), ms)

    @Test
    fun `today, the last seven days and all time add up the days listened`() {
        val stats = listeningStatsOf(
            days = listOf(day(0, 1 * hour), day(3, 2 * hour), day(6, 4 * hour), day(7, 8 * hour), day(30, 16 * hour)),
            finished = emptyList(),
            today = today,
        )

        stats.todayMs shouldBe 1 * hour
        stats.lastSevenDaysMs shouldBe 7 * hour
        stats.allTimeMs shouldBe 31 * hour
    }

    @Test
    fun `the chart has each of the last seven days, oldest first, days not listened at nothing`() {
        val stats = listeningStatsOf(listOf(day(0, hour), day(2, 2 * hour)), emptyList(), today)

        stats.chart shouldBe listOf(
            today.minusDays(6) to 0L,
            today.minusDays(5) to 0L,
            today.minusDays(4) to 0L,
            today.minusDays(3) to 0L,
            today.minusDays(2) to 2 * hour,
            today.minusDays(1) to 0L,
            today to hour,
        )
    }

    @Test
    fun `books finished are counted, the latest first`() {
        val older = FinishedBookEntity("a", "Caste", finishedAt = 1L)
        val newer = FinishedBookEntity("b", "Dune", finishedAt = 2L)

        val stats = listeningStatsOf(emptyList(), listOf(older, newer), today)

        stats.booksFinished shouldBe 2
        stats.recentlyFinished.map { it.title } shouldBe listOf("Dune", "Caste")
    }

    @Test
    fun `a length reads in hours and minutes`() {
        formatListened(0L) shouldBe "0 min"
        formatListened(59_999L) shouldBe "0 min"
        formatListened(45 * 60_000L) shouldBe "45 min"
        formatListened(3 * hour + 20 * 60_000L) shouldBe "3 h 20 min"
        formatListened(2 * hour) shouldBe "2 h"
    }
}
