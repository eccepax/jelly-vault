package org.jellyfin.mobile.library

import org.jellyfin.mobile.data.entity.AudiobookEntryEntity

/** The most books "Continue listening" holds. */
internal const val CONTINUE_LISTENING_LIMIT = 10

/**
 * The books to carry on with: started and not finished, the one listened to last first, leaving out
 * those hidden ([hiddenIds], by the library item's id). At most [limit] of them.
 */
internal fun continueListening(
    entries: List<AudiobookEntryEntity>,
    hiddenIds: Set<String> = emptySet(),
    limit: Int = CONTINUE_LISTENING_LIMIT,
): List<LibraryItem.AudiobookBundle> = entries
    .filter { it.playedPercentage > 0f && it.playedPercentage < FINISHED_PERCENT && it.lastPlayed.isNotEmpty() }
    // ISO-8601 times, so the newest sorts last as text
    .sortedByDescending { it.lastPlayed }
    .map { it.toLibraryItem() }
    .filter { it.id.toString() !in hiddenIds }
    .take(limit)
