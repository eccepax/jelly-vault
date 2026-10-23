package org.jellyfin.mobile.books.progress

import org.jellyfin.mobile.books.data.entity.BookProgressEntity
import java.time.Instant

/*
 * The server keeps a reader's place in a book as a position in the book's runtime, like a place in
 * a track: a PDF runs 10,000 ticks a page, an EPUB a second. A page (or EPUB chapter) is its share
 * of that runtime, so another client's place in the same book reads back as the same page.
 */

/** Where [page] of [totalPages] starts, in ticks of [runTimeTicks]. Rounded up, so it reads back as itself. */
internal fun pageTicks(page: Int, totalPages: Int, runTimeTicks: Long): Long =
    if (totalPages > 0) (page.toLong() * runTimeTicks + totalPages - 1) / totalPages else 0L

/** The page of [totalPages] that [ticks] of [runTimeTicks] is on. */
internal fun pageAt(ticks: Long, runTimeTicks: Long, totalPages: Int): Int =
    (ticks * totalPages / runTimeTicks).toInt().coerceIn(0, totalPages - 1)

/** How far into a book the reader is, as the library filters it. */
enum class ReadingState { UNREAD, IN_PROGRESS, FINISHED }

/**
 * How far into a book [page] of [totalPages] is: finished on the last page (or EPUB chapter). Opening
 * a one-page book isn't finishing it, and a book whose length isn't known yet is unread.
 */
fun readingState(page: Int, totalPages: Int): ReadingState = when {
    page <= 0 || totalPages <= 0 -> ReadingState.UNREAD
    page >= totalPages - 1 -> ReadingState.FINISHED
    else -> ReadingState.IN_PROGRESS
}

/** How far into the book the reader is, by whichever place was read most recently: [local] or [server]'s. */
fun readingState(local: BookProgressEntity?, server: ServerBookProgress): ReadingState = when {
    server.isNewerThan(local) -> server.state
    local != null -> readingState(local.pageNumber, local.totalPages)
    else -> ReadingState.UNREAD
}

/** The reader's place in a book as the server keeps it. */
data class ServerBookProgress(
    val positionTicks: Long,
    /** The book's length as the server counts it (see [pageTicks]); null if it has none. */
    val runTimeTicks: Long?,
    val played: Boolean,
    val lastPlayed: Instant?,
) {
    /**
     * Whether the position can be read as a page: within the book. BookVault used to send a share
     * of a made-up 100 s instead, far past a book's end, and those mean nothing.
     */
    private val usable: Boolean
        get() = runTimeTicks != null && runTimeTicks > 0 && positionTicks in 0..runTimeTicks

    /** When the reader was last at [page], or null if the server knows nothing usable. */
    val knownAt: Instant? get() = lastPlayed.takeIf { usable }

    /**
     * Whether this place wins over [local]: the newest place wins, here or on another device.
     * Compared to the millisecond, as places are saved here, so one taken from the server isn't newer
     * than itself.
     */
    fun isNewerThan(local: BookProgressEntity?): Boolean {
        val at = knownAt ?: return false
        return local == null || at.toEpochMilli() > local.lastUpdated
    }

    /** How far into the book the server has the reader. A finished book keeps its place at the end. */
    val state: ReadingState
        get() = when {
            played -> ReadingState.FINISHED
            positionTicks > 0 -> ReadingState.IN_PROGRESS
            else -> ReadingState.UNREAD
        }

    /**
     * The page of [totalPages] the server has the reader on, or null if it knows nothing usable. A
     * book marked played has no position left, and opens at its end.
     */
    fun page(totalPages: Int): Int? = when {
        !usable || totalPages <= 0 -> null
        positionTicks > 0 -> pageAt(positionTicks, runTimeTicks!!, totalPages)
        played -> totalPages - 1
        else -> 0
    }
}
