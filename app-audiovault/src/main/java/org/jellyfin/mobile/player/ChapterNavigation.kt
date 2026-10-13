package org.jellyfin.mobile.player

import org.jellyfin.sdk.model.api.ChapterInfo
import org.jellyfin.sdk.model.extensions.ticks
import kotlin.time.Duration

/**
 * Chapter start-position lookups shared by [AudiobookPlayerViewModel] (previous/next chapter
 * buttons) and `SleepTimerManager` (end-of-chapter timer).
 */
internal object ChapterNavigation {

    /**
     * Start position of the chapter containing [playbackPosition], or null if [chapters] is
     * empty or [playbackPosition] is before the first chapter's start.
     */
    fun currentChapterStartPosition(chapters: List<ChapterInfo>, playbackPosition: Duration): Duration? {
        val startPositions = chapters.map { it.startPositionTicks.ticks }
        return startPositions.findLast { pos -> playbackPosition >= pos }
    }

    /**
     * Start position of the chapter after the one containing [playbackPosition].
     * - Null if [playbackPosition] is before the first chapter's start (no "current" chapter).
     * - [Duration.INFINITE] if the current chapter is the last one (no next chapter).
     */
    fun nextChapterStartPosition(chapters: List<ChapterInfo>, playbackPosition: Duration): Duration? {
        val startPositions = chapters.map { it.startPositionTicks.ticks }
        val currentChapterIndex = startPositions.indexOfLast { pos -> playbackPosition >= pos }
        if (currentChapterIndex == -1) return null
        val nextChapterIndex = currentChapterIndex + 1
        return startPositions.getOrElse(nextChapterIndex) { Duration.INFINITE }
    }
}
