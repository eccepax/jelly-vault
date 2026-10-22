package org.jellyfin.mobile.books.progress

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
