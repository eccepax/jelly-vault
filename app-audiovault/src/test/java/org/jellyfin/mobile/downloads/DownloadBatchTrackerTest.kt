package org.jellyfin.mobile.downloads

import io.kotest.matchers.shouldBe
import org.junit.Test
import java.util.UUID

/**
 * Unit coverage for [DownloadBatchTracker] — the piece that lets [JellyfinDownloadService] post
 * exactly one "Downloaded" notification per book instead of one per chapter file, fixing a real
 * user-reported bug (notification spam during a multi-track audiobook download).
 */
class DownloadBatchTrackerTest {

    private fun contentId() = UUID.randomUUID().toString()

    @Test
    fun `a single-track download resolves immediately with its own byte count`() {
        val trackId = contentId()
        DownloadBatchTracker.trackStarted("Solo Track", trackId)

        DownloadBatchTracker.trackFinished(trackId, 500L) shouldBe ("Solo Track" to 500L)
    }

    @Test
    fun `a multi-track book only resolves once every track has finished, summing bytes`() {
        val title = "Three Chapter Book " + System.nanoTime()
        val track1 = contentId()
        val track2 = contentId()
        val track3 = contentId()
        DownloadBatchTracker.trackStarted(title, track1)
        DownloadBatchTracker.trackStarted(title, track2)
        DownloadBatchTracker.trackStarted(title, track3)

        DownloadBatchTracker.trackFinished(track1, 100L) shouldBe null
        DownloadBatchTracker.trackFinished(track2, 200L) shouldBe null
        DownloadBatchTracker.trackFinished(track3, 300L) shouldBe (title to 600L)
    }

    @Test
    fun `two books downloading at the same time do not interfere with each other's counts`() {
        val bookA = "Book A " + System.nanoTime()
        val bookB = "Book B " + System.nanoTime()
        val trackA1 = contentId()
        val trackA2 = contentId()
        val trackB1 = contentId()
        DownloadBatchTracker.trackStarted(bookA, trackA1)
        DownloadBatchTracker.trackStarted(bookA, trackA2)
        DownloadBatchTracker.trackStarted(bookB, trackB1)

        DownloadBatchTracker.trackFinished(trackB1, 50L) shouldBe (bookB to 50L)
        DownloadBatchTracker.trackFinished(trackA1, 10L) shouldBe null
        DownloadBatchTracker.trackFinished(trackA2, 20L) shouldBe (bookA to 30L)
    }

    @Test
    fun `finishing an untracked track is a no-op, not a crash`() {
        DownloadBatchTracker.trackFinished(contentId(), 100L) shouldBe null
    }
}
