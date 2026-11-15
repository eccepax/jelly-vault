package org.jellyfin.mobile.stats

import org.jellyfin.mobile.data.entity.FinishedBookEntity
import org.jellyfin.mobile.data.entity.ListeningDayEntity
import java.time.LocalDate

/**
 * The listening stats: today's, the last seven days' (today among them), and all of it; the last
 * seven days one by one for the [chart] (oldest first); and the books finished.
 */
data class ListeningStats(
    val todayMs: Long = 0L,
    val lastSevenDaysMs: Long = 0L,
    val allTimeMs: Long = 0L,
    val chart: List<Pair<LocalDate, Long>> = emptyList(),
    val booksFinished: Int = 0,
    val recentlyFinished: List<FinishedBookEntity> = emptyList(),
)

/** The stats from the [days] listened and the books [finished], as of [today]. */
fun listeningStatsOf(
    days: List<ListeningDayEntity>,
    finished: List<FinishedBookEntity>,
    today: LocalDate,
): ListeningStats {
    val byDay = days.associate { LocalDate.parse(it.day) to it.listenedMs }
    val chart = (CHART_DAYS - 1 downTo 0).map { ago -> today.minusDays(ago.toLong()).let { it to (byDay[it] ?: 0L) } }
    return ListeningStats(
        todayMs = byDay[today] ?: 0L,
        lastSevenDaysMs = chart.sumOf { it.second },
        allTimeMs = byDay.values.sum(),
        chart = chart,
        booksFinished = finished.size,
        recentlyFinished = finished.sortedByDescending { it.finishedAt }.take(RECENT_BOOKS),
    )
}

/** [ms] as hours and minutes: "3 h 20 min", "2 h", "45 min". */
fun formatListened(ms: Long): String {
    val minutes = ms / MINUTE_MS
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return when {
        hours == 0L -> "$rest min"
        rest == 0L -> "$hours h"
        else -> "$hours h $rest min"
    }
}

private const val CHART_DAYS = 7
private const val RECENT_BOOKS = 5
private const val MINUTE_MS = 60_000L
private const val MINUTES_PER_HOUR = 60L
