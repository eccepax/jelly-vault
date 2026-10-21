package org.jellyfin.mobile.player.playback

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.player.mediasegments.MediaSegmentAction
import org.jellyfin.mobile.player.playback.MediaSegmentSkipper.Companion.TICK_MS
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Segments the server marks in a track (an intro, a recap, an outro): skipped by themselves, or
 * offered to the listener to skip, as the settings say for each kind.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaSegmentSkipperTest {

    private val controller = FakeAudiobookController()
    private val itemId = UUID.fromString("00000000-0000-4000-8000-000000000001")
    private val track = AudiobookTrack(id = itemId.toString(), title = "Part 1", durationMs = 3_600_000L)

    private val intro = segment(MediaSegmentType.INTRO, 10_000L, 40_000L)
    private val outro = segment(MediaSegmentType.OUTRO, 3_500_000L, 3_590_000L)
    private val segments = mutableMapOf(itemId.toString() to listOf(intro, outro))
    private val actions = mutableMapOf(MediaSegmentType.INTRO to MediaSegmentAction.SKIP)

    private fun TestScope.skipper() = MediaSegmentSkipper(
        controller,
        segmentsOf = { id -> segments[id].orEmpty() },
        actionOf = { actions[it.type] ?: MediaSegmentAction.NOTHING },
        scope = backgroundScope,
    ).also {
        it.start()
        runCurrent()
    }

    private fun TestScope.wait(ms: Long = TICK_MS) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun `a segment set to skip is skipped when playback reaches it`() = runTest {
        skipper()
        controller.playing(track, positionMs = 9_000L)
        runCurrent()
        wait()
        controller.commands.shouldBeEmpty()

        controller.positionMs = 10_200L
        wait()

        controller.commands shouldBe listOf("seekTo 40000")
    }

    @Test
    fun `starting inside a segment set to skip skips it at once`() = runTest {
        skipper()

        controller.playing(track, positionMs = 20_000L)
        runCurrent()
        wait()

        controller.commands shouldBe listOf("seekTo 40000")
    }

    @Test
    fun `a segment already passed is left alone`() = runTest {
        skipper()

        controller.playing(track, positionMs = 60_000L)
        runCurrent()
        wait()

        controller.commands.shouldBeEmpty()
    }

    @Test
    fun `inside a segment to ask about, the listener is offered to skip it, and only there`() = runTest {
        actions[MediaSegmentType.OUTRO] = MediaSegmentAction.ASK_TO_SKIP
        val skipper = skipper()
        controller.playing(track, positionMs = 3_400_000L)
        runCurrent()
        wait()
        skipper.offered.value shouldBe null

        controller.positionMs = 3_550_000L
        wait()
        skipper.offered.value shouldBe outro

        controller.positionMs = 3_595_000L
        wait()
        skipper.offered.value shouldBe null
    }

    @Test
    fun `skipping the offered segment jumps just past its end`() = runTest {
        actions[MediaSegmentType.OUTRO] = MediaSegmentAction.ASK_TO_SKIP
        val skipper = skipper()
        controller.playing(track, positionMs = 3_550_000L)
        runCurrent()
        wait()

        skipper.skipOffered()

        controller.commands shouldBe listOf("seekTo 3590001")
    }

    @Test
    fun `a segment set to skip by itself is never offered`() = runTest {
        val skipper = skipper()
        controller.playing(track, positionMs = 20_000L)
        runCurrent()
        wait()

        skipper.offered.value shouldBe null
    }

    @Test
    fun `another track's segments take over when it starts`() = runTest {
        val other =
            AudiobookTrack(id = "00000000-0000-4000-8000-000000000002", title = "Part 2", durationMs = 3_600_000L)
        segments[other.id] = emptyList()
        skipper()
        controller.playing(other, positionMs = 20_000L)
        runCurrent()
        wait()

        controller.commands.shouldBeEmpty()
    }

    private fun segment(type: MediaSegmentType, startMs: Long, endMs: Long) = MediaSegmentDto(
        id = UUID.randomUUID(),
        itemId = itemId,
        type = type,
        startTicks = startMs * 10_000L,
        endTicks = endMs * 10_000L,
    )
}
