package org.jellyfin.mobile.library

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

/** Regression coverage for [allTracksDownloaded], which picks the download-menu action label. */
class AllTracksDownloadedTest {

    private val trackA = UUID.randomUUID()
    private val trackB = UUID.randomUUID()

    @Test
    fun `a single downloaded track is fully downloaded`() {
        allTracksDownloaded(listOf(trackA), setOf(trackA.toString())) shouldBe true
    }

    @Test
    fun `a single not-yet-downloaded track is not fully downloaded`() {
        allTracksDownloaded(listOf(trackA), emptySet()) shouldBe false
    }

    @Test
    fun `a bundle is only fully downloaded once every track is present`() {
        allTracksDownloaded(listOf(trackA, trackB), setOf(trackA.toString())) shouldBe false
        allTracksDownloaded(listOf(trackA, trackB), setOf(trackA.toString(), trackB.toString())) shouldBe true
    }

    @Test
    fun `an empty track list is never treated as fully downloaded`() {
        // Guards the "Remove Download" label from being shown for a menu with nothing in it.
        allTracksDownloaded(emptyList(), emptySet()) shouldBe false
    }
}
