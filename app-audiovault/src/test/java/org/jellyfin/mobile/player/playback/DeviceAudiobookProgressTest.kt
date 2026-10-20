package org.jellyfin.mobile.player.playback

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Listening to a downloaded track, kept with the download for the server to hear about later. */
class DeviceAudiobookProgressTest {

    private val itemId = UUID.fromString("00000000-0000-4000-8000-000000000001")
    private val track =
        AudiobookTrack(id = itemId.toString(), title = "Part 1", durationMs = 3_600_000L, fromDevice = true)
    private val source = mockk<LocalJellyfinMediaSource>(relaxed = true)
    private val saved = mutableListOf<LocalJellyfinMediaSource>()
    private val at = Instant.parse("2026-10-20T08:00:00Z")
    private var downloaded = true

    private val progress = DeviceAudiobookProgress(
        download = { id -> source.takeIf { downloaded && id == itemId } },
        save = { saved += it },
        now = { at },
    )

    @Test
    fun `pausing keeps where it was paused, and when, for the server to hear about later`() = runTest {
        progress.progress(track, positionMs = 754_000L, isPaused = true)

        verify { source.startTime = 754_000L.milliseconds }
        verify { source.positionSavedAt = at }
        verify { source.finished = false }
        saved shouldBe listOf(source)
    }

    @Test
    fun `stopping part way keeps where it stopped`() = runTest {
        progress.stopped(track, positionMs = 1_000L)

        verify { source.startTime = 1_000L.milliseconds }
        saved shouldBe listOf(source)
    }

    @Test
    fun `finishing keeps it as finished, to start over next time`() = runTest {
        progress.finished(track)

        verify { source.startTime = Duration.ZERO }
        verify { source.finished = true }
    }

    @Test
    fun `starting keeps nothing, it starts where it was left`() = runTest {
        progress.started(track, positionMs = 5_000L)

        saved.shouldBeEmpty()
    }

    @Test
    fun `a download removed meanwhile is left alone`() = runTest {
        downloaded = false

        progress.stopped(track, positionMs = 1_000L)

        saved.shouldBeEmpty()
    }
}
