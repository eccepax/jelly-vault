package org.jellyfin.mobile.library

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.extensions.ticks
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Resume-point and progress calculations shared between playback-queue construction
 * ([LibraryViewModel]'s `buildResumeQueue`) and the library progress bar (`toBundleEntry`).
 *
 * Kept independent of the Jellyfin API client and Room so the exact logic behind two past
 * regressions can be unit tested directly:
 * - Resume always restarting at track 0 (fixed by computing resume from track `userData` instead
 *   of a `parentId` that Jellyfin often omits).
 * - The progress bar showing aggregate time consumed across all tracks regardless of order, which
 *   diverges from the actual resume point whenever tracks were played out of sequence.
 */
internal object AudiobookProgress {
    private const val PERCENTAGE_SCALE = 100f

    private const val RESUME_REWIND_SECONDS = 15L

    data class ResumePoint(val index: Int, val position: Duration?)

    /**
     * Index of the first track not fully played, in [tracks] order, or -1 if every track is
     * played (the whole book is finished).
     */
    fun resumeIndex(tracks: List<BaseItemDto>): Int = tracks.indexOfFirst { it.userData?.played != true }

    /**
     * Resume index/position for starting playback: the first not-fully-played track (or index 0
     * to restart from the beginning if every track is already played), rewound [RESUME_REWIND_SECONDS]
     * from its saved position so playback starts slightly before where it left off.
     */
    fun resumePoint(tracks: List<BaseItemDto>): ResumePoint {
        val effectiveIndex = resumeIndex(tracks).coerceAtLeast(0)
        val rawPosition = tracks.getOrNull(effectiveIndex)?.userData?.playbackPositionTicks
            ?.takeIf { it > 0L }
            ?.ticks
        val position = rawPosition?.minus(RESUME_REWIND_SECONDS.seconds)?.takeIf { it > Duration.ZERO }
        return ResumePoint(effectiveIndex, position)
    }

    /**
     * Overall book completion as a percentage of total runtime consumed up to the resume point
     * (not the sum of each track's own played/position data), so it always matches where
     * playback actually resumes, even when tracks were played out of order.
     */
    fun playedPercentage(tracks: List<BaseItemDto>): Float {
        val totalTicks = tracks.sumOf { it.runTimeTicks ?: 0L }
        if (totalTicks <= 0L) return 0f
        val idx = resumeIndex(tracks)
        val playedTicks = if (idx == -1) {
            totalTicks
        } else {
            tracks.take(idx).sumOf { it.runTimeTicks ?: 0L } + (tracks[idx].userData?.playbackPositionTicks ?: 0L)
        }
        return playedTicks.toFloat() / totalTicks * PERCENTAGE_SCALE
    }
}
