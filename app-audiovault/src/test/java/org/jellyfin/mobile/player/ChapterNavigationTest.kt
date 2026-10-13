package org.jellyfin.mobile.player

import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.ChapterInfo
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Basic functionality coverage for chapter start-position lookups used by chapter skip buttons and the end-of-chapter sleep timer. */
class ChapterNavigationTest {

    private fun chapter(startSeconds: Long) = ChapterInfo(
        startPositionTicks = startSeconds * TICKS_PER_SECOND,
        name = null,
        imagePath = null,
        imageDateModified = LocalDateTime.now(),
        imageTag = null,
    )

    private val chapters = listOf(chapter(0), chapter(60), chapter(180))

    // --- currentChapterStartPosition --------------------------------------------------------

    @Test
    fun `finds the start of the chapter containing the playback position`() {
        ChapterNavigation.currentChapterStartPosition(chapters, 90.seconds) shouldBe 60.seconds
    }

    @Test
    fun `returns the exact chapter start when playback position lands exactly on a boundary`() {
        ChapterNavigation.currentChapterStartPosition(chapters, 60.seconds) shouldBe 60.seconds
    }

    @Test
    fun `returns the last chapter when playback position is past the final chapter start`() {
        ChapterNavigation.currentChapterStartPosition(chapters, 300.seconds) shouldBe 180.seconds
    }

    @Test
    fun `current chapter returns null when there are no chapters`() {
        ChapterNavigation.currentChapterStartPosition(emptyList(), 90.seconds) shouldBe null
    }

    // --- nextChapterStartPosition ------------------------------------------------------------

    @Test
    fun `finds the start of the next chapter`() {
        ChapterNavigation.nextChapterStartPosition(chapters, 90.seconds) shouldBe 180.seconds
    }

    @Test
    fun `returns infinite when already in the last chapter`() {
        ChapterNavigation.nextChapterStartPosition(chapters, 200.seconds) shouldBe Duration.INFINITE
    }

    @Test
    fun `returns null when playback position is before the first chapter`() {
        // Only possible with malformed chapter data (first chapter not starting at 0), but the
        // lookup should degrade gracefully rather than throw or return a nonsensical index.
        val laterChapters = listOf(chapter(10), chapter(60))
        ChapterNavigation.nextChapterStartPosition(laterChapters, 5.seconds) shouldBe null
    }

    @Test
    fun `next chapter returns null when there are no chapters`() {
        ChapterNavigation.nextChapterStartPosition(emptyList(), 90.seconds) shouldBe null
    }

    private companion object {
        const val TICKS_PER_SECOND = 10_000_000L
    }
}
