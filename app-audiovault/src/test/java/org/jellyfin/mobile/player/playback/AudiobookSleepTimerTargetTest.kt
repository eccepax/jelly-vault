package org.jellyfin.mobile.player.playback

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.common.playback.PlaybackSample
import org.jellyfin.mobile.common.playback.SleepTimer
import org.jellyfin.mobile.common.playback.SleepTimerState
import org.junit.jupiter.api.Test

/** The shared sleep timer on AudioVault's player: "end of segment" is the end of the chapter. */
@OptIn(ExperimentalCoroutinesApi::class)
class AudiobookSleepTimerTargetTest {

    private val controller = FakeAudiobookController()
    private val target = AudiobookSleepTimerTarget(controller)
    private val chapters = listOf(Chapter(0L, "One"), Chapter(600_000L, "Two"), Chapter(1_500_000L, "Three"))
    private val book = AudiobookTrack(id = "book", title = "Book", durationMs = 3_000_000L, chapters = chapters)
    private val partOne = AudiobookTrack(id = "one", title = "Part 1", durationMs = 900_000L)
    private val partTwo = AudiobookTrack(id = "two", title = "Part 2", durationMs = 900_000L)

    @Test
    fun `in a book with chapters, the segment is the chapter playing`() {
        controller.playing(book, positionMs = 700_000L)

        val sample = target.sample()
        sample.positionMs shouldBe 100_000L
        sample.durationMs shouldBe 900_000L
        sample.isPlaying shouldBe true
        sample.hasNext shouldBe true
    }

    @Test
    fun `the last chapter runs to the end of the track`() {
        controller.playing(book, positionMs = 2_000_000L)

        target.sample().durationMs shouldBe 1_500_000L
        target.sample().hasNext shouldBe false
    }

    @Test
    fun `moving into the next chapter is moving to another segment`() {
        controller.playing(book, positionMs = 599_000L)
        val before = target.sample().segmentId

        controller.positionMs = 600_500L

        target.sample().segmentId shouldNotBe before
    }

    @Test
    fun `without chapters, the segment is the track, and another part of the book comes next`() {
        controller.playing(partOne, partTwo, positionMs = 300_000L)

        target.sample() shouldBe PlaybackSample("one", 300_000L, 900_000L, isPlaying = true, hasNext = true)
    }

    @Test
    fun `the last chapter of one part still has the next part after it`() {
        controller.playing(book, partTwo, positionMs = 2_000_000L)

        target.sample().hasNext shouldBe true
    }

    @Test
    fun `back to the start of the segment is the start of the chapter playing`() {
        controller.playing(book, positionMs = 600_300L)

        target.rewindToSegmentStart()

        controller.commands shouldBe listOf("seekTo 600000")
    }

    @Test
    fun `fading turns the player down`() {
        target.setFade(0.4f)

        controller.volume shouldBe 0.4f
    }

    @Test
    fun `end of chapter pauses when the chapter plays out, back at the start of the next`() = runTest {
        val timer = timer()
        controller.playing(book, positionMs = 590_000L)
        timer.stopAtEndOfSegment()

        play(10_000L)
        controller.positionMs = 600_400L
        wait(SleepTimer.TICK_MS)

        controller.commands shouldBe listOf("pause", "seekTo 600000")
        timer.state.value shouldBe SleepTimerState.Off
    }

    private fun TestScope.timer() = SleepTimer(target, backgroundScope).also { runCurrent() }

    private fun TestScope.wait(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    private fun TestScope.play(ms: Long) {
        var left = ms
        while (left > 0) {
            controller.positionMs += SleepTimer.TICK_MS
            wait(SleepTimer.TICK_MS)
            left -= SleepTimer.TICK_MS
        }
    }
}
