package org.jellyfin.mobile.library

import org.jellyfin.mobile.data.entity.AudiobookEntryEntity
import java.util.UUID

/** A book to play next, after one of its series ended: its [title] and its tracks. */
data class NextBook(val title: String, val trackIds: List<UUID>)

/**
 * The book after the one [trackId] is in, among the library's [entries], when that's a book of a
 * series (a book of several files shown as separate books); null for the last of a series, a book in
 * none, or a track in no book of the library.
 */
internal fun nextInSeries(entries: List<AudiobookEntryEntity>, trackId: String): NextBook? {
    val current = entries.firstOrNull { it.seriesKey != null && trackId in it.trackIds.split(",") } ?: return null
    val place = current.seriesIndex ?: return null
    val next = entries
        .filter { it.seriesKey == current.seriesKey && (it.seriesIndex ?: place) > place }
        .minByOrNull { it.seriesIndex ?: place }
        ?: return null
    return NextBook(next.title, next.toLibraryItem().trackIds)
}
