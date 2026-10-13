package org.jellyfin.mobile.library

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * Regression coverage for the resume-point and progress-bar logic behind two fixed bugs:
 * - Resume always restarting at track 0 for multi-track bundles.
 * - The library progress bar showing aggregate consumption instead of the actual resume point.
 */
class AudiobookProgressTest {

    private fun track(
        played: Boolean = false,
        positionTicks: Long = 0L,
        runTimeTicks: Long = 100 * TICKS_PER_SECOND,
    ): BaseItemDto = BaseItemDto(
        id = UUID.randomUUID(),
        type = BaseItemKind.AUDIO_BOOK,
        runTimeTicks = runTimeTicks,
        userData = UserItemDataDto(
            rating = null,
            playedPercentage = null,
            unplayedItemCount = null,
            playbackPositionTicks = positionTicks,
            playCount = 0,
            isFavorite = false,
            likes = null,
            lastPlayedDate = null,
            played = played,
            key = "",
            itemId = UUID.randomUUID(),
        ),
    )

    // --- resumePoint --------------------------------------------------------------------------

    @Test
    fun `resumes at track 0 when nothing has been played`() {
        val tracks = listOf(track(), track(), track())
        val point = AudiobookProgress.resumePoint(tracks)
        point.index shouldBe 0
        point.position shouldBe null
    }

    @Test
    fun `resumes at the first not-fully-played track, skipping earlier played tracks`() {
        // Regression: previously, a null parentId from Jellyfin's recursive query meant this
        // case fell back to a code path with no resume logic at all — always track 0, position 0.
        val tracks = listOf(
            track(played = true),
            track(played = true),
            track(played = false, positionTicks = 30 * TICKS_PER_SECOND),
            track(played = false),
        )
        val point = AudiobookProgress.resumePoint(tracks)
        point.index shouldBe 2
        point.position shouldBe 15.seconds // 30s saved position, rewound 15s
    }

    @Test
    fun `resume position is rewound 15 seconds from the saved position`() {
        val tracks = listOf(track(positionTicks = 60 * TICKS_PER_SECOND))
        AudiobookProgress.resumePoint(tracks).position shouldBe 45.seconds
    }

    @Test
    fun `resume position is null when the saved position is within the rewind window`() {
        val tracks = listOf(track(positionTicks = 10 * TICKS_PER_SECOND))
        AudiobookProgress.resumePoint(tracks).position shouldBe null
    }

    @Test
    fun `resume position is null when the track has no saved position`() {
        val tracks = listOf(track(positionTicks = 0L))
        AudiobookProgress.resumePoint(tracks).position shouldBe null
    }

    @Test
    fun `restarts from track 0 when every track is already played`() {
        val tracks = listOf(track(played = true), track(played = true))
        val point = AudiobookProgress.resumePoint(tracks)
        point.index shouldBe 0
        point.position shouldBe null
    }

    @Test
    fun `finds the resume track even when played tracks are scattered non-sequentially`() {
        // Mirrors real-world data: a reader who sampled several chapters out of order.
        val tracks = listOf(
            track(played = true),
            track(played = false, positionTicks = 5 * TICKS_PER_SECOND), // resume here
            track(played = false),
            track(played = true),
        )
        AudiobookProgress.resumePoint(tracks).index shouldBe 1
    }

    // --- playedPercentage ----------------------------------------------------------------------

    @Test
    fun `percentage is zero when nothing has been played`() {
        val tracks = listOf(track(runTimeTicks = 100 * TICKS_PER_SECOND), track(runTimeTicks = 100 * TICKS_PER_SECOND))
        AudiobookProgress.playedPercentage(tracks) shouldBe (0f plusOrMinus 0.001f)
    }

    @Test
    fun `percentage is 100 when every track is played`() {
        val tracks = listOf(
            track(played = true, runTimeTicks = 100 * TICKS_PER_SECOND),
            track(played = true, runTimeTicks = 200 * TICKS_PER_SECOND),
        )
        AudiobookProgress.playedPercentage(tracks) shouldBe (100f plusOrMinus 0.001f)
    }

    @Test
    fun `percentage matches the resume point, not aggregate consumption across all tracks`() {
        // Regression: the old calculation summed every track's own played/position data
        // (counting later "sampled" chapters), which could show a large percentage (e.g. ~18%)
        // even though resume lands right at the very start of the book.
        val tracks = listOf(
            track(played = true, runTimeTicks = 50 * TICKS_PER_SECOND), // short front matter, played
            track(
                played = false,
                positionTicks = 10 * TICKS_PER_SECOND,
                runTimeTicks = 1_000 * TICKS_PER_SECOND,
            ), // resumes here
            track(played = false, runTimeTicks = 1_000 * TICKS_PER_SECOND),
            // A later chapter marked played from non-sequential sampling must NOT inflate the
            // percentage past the resume point.
            track(played = true, runTimeTicks = 3_000 * TICKS_PER_SECOND),
        )
        val total = 50 + 1_000 + 1_000 + 3_000
        val expected = (50 + 10).toFloat() / total * 100f
        AudiobookProgress.playedPercentage(tracks) shouldBe (expected plusOrMinus 0.001f)
    }

    @Test
    fun `percentage is zero for an empty or zero-runtime track list`() {
        AudiobookProgress.playedPercentage(emptyList()) shouldBe 0f
        AudiobookProgress.playedPercentage(listOf(track(runTimeTicks = 0L))) shouldBe 0f
    }

    private companion object {
        const val TICKS_PER_SECOND = 10_000_000L
    }
}
