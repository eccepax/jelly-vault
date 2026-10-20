package org.jellyfin.mobile.common.playback

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.common.playback.SleepTimer.Companion.FADE_MS
import org.jellyfin.mobile.common.playback.SleepTimer.Companion.TICK_MS
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {

    private val target = FakeSleepTimerTarget()
    private val minute = 60_000L

    private fun TestScope.timer() = SleepTimer(target, backgroundScope).also { runCurrent() }

    private fun TestScope.wait(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    /** Playing [segment] (a song, a chapter) of [durationMs], [positionMs] into it. */
    private fun at(
        segment: String,
        positionMs: Long,
        durationMs: Long = 180_000L,
        isPlaying: Boolean = true,
        hasNext: Boolean = true,
    ) {
        target.sample = PlaybackSample(segment, positionMs, durationMs, isPlaying, hasNext)
    }

    @Test
    fun `off until set`() = runTest {
        timer().state.value shouldBe SleepTimerState.Off
    }

    @Test
    fun `counts down, then pauses and turns itself off`() = runTest {
        val timer = timer()
        at("a", 0L)

        timer.start(15 * minute)
        timer.state.value shouldBe SleepTimerState.Counting(remainingMs = 15 * minute)
        wait(5 * minute)
        timer.state.value shouldBe SleepTimerState.Counting(remainingMs = 10 * minute)

        wait(10 * minute)

        target.commands shouldBe listOf("pause")
        timer.state.value shouldBe SleepTimerState.Off
    }

    @Test
    fun `fades out over the last seconds rather than cutting off`() = runTest {
        val timer = timer()
        at("a", 0L)
        timer.start(minute)

        wait(minute - FADE_MS)
        target.fades.shouldBeEmpty()
        wait(FADE_MS / 2)
        target.fades.last() shouldBeLessThan 0.6f
        wait(FADE_MS / 2)

        target.commands shouldBe listOf("pause")
        target.fades.last() shouldBe 1f
    }

    @Test
    fun `turning it off stops the countdown, and a fade under way is undone`() = runTest {
        val timer = timer()
        at("a", 0L)
        timer.start(minute)
        wait(minute - FADE_MS / 2)

        timer.cancel()
        wait(minute)

        target.commands.shouldBeEmpty()
        target.fades.last() shouldBe 1f
        timer.state.value shouldBe SleepTimerState.Off
    }

    @Test
    fun `more time adds fifteen minutes, undoing any fade`() = runTest {
        val timer = timer()
        at("a", 0L)
        timer.start(minute)
        wait(minute - FADE_MS / 2)

        timer.extend()

        timer.state.value shouldBe SleepTimerState.Counting(remainingMs = FADE_MS / 2 + 15 * minute)
        target.fades.last() shouldBe 1f
    }

    @Test
    fun `choosing another time replaces the first`() = runTest {
        val timer = timer()
        at("a", 0L)
        timer.start(minute)

        timer.start(30 * minute)
        wait(2 * minute)

        target.commands.shouldBeEmpty()
        timer.state.value shouldBe SleepTimerState.Counting(remainingMs = 28 * minute)
    }

    @Test
    fun `end of segment pauses when it plays out, back at the start of the next`() = runTest {
        val timer = timer()
        at("a", 0L)
        timer.stopAtEndOfSegment()
        timer.state.value shouldBe SleepTimerState.EndOfSegment

        at("a", 179_500L)
        wait(TICK_MS)
        at("b", 100L)
        wait(TICK_MS)

        target.commands shouldBe listOf("pause", "rewindToSegmentStart")
        timer.state.value shouldBe SleepTimerState.Off
    }

    @Test
    fun `end of segment keeps waiting when you skip to another`() = runTest {
        val timer = timer()
        at("a", 30_000L)
        timer.stopAtEndOfSegment()

        wait(TICK_MS)
        at("b", 0L)
        wait(TICK_MS)

        target.commands.shouldBeEmpty()
        timer.state.value shouldBe SleepTimerState.EndOfSegment
    }

    @Test
    fun `end of segment turns off when the last one ends by itself`() = runTest {
        val timer = timer()
        at("a", 0L)
        timer.stopAtEndOfSegment()

        at("a", 180_000L, isPlaying = false, hasNext = false)
        wait(TICK_MS)

        timer.state.value shouldBe SleepTimerState.Off
    }

    @Test
    fun `end of segment survives a pause in its last seconds, and still pauses after it`() = runTest {
        val timer = timer()
        at("chapter 1", 0L)
        timer.stopAtEndOfSegment()

        at("chapter 1", 178_500L, isPlaying = false)
        wait(minute)
        timer.state.value shouldBe SleepTimerState.EndOfSegment

        at("chapter 1", 179_500L)
        wait(TICK_MS)
        at("chapter 2", 100L)
        wait(TICK_MS)

        target.commands shouldBe listOf("pause", "rewindToSegmentStart")
        timer.state.value shouldBe SleepTimerState.Off
    }

    @Test
    fun `end of segment follows the playback position, so a long pause doesn't set it off`() = runTest {
        val timer = timer()
        at("chapter 1", 60_000L)
        timer.stopAtEndOfSegment()

        at("chapter 1", 60_000L, isPlaying = false)
        wait(10 * minute)

        target.commands.shouldBeEmpty()
        timer.state.value shouldBe SleepTimerState.EndOfSegment
    }

    @Test
    fun `end of segment still catches the end when playing at double speed`() = runTest {
        val timer = timer()
        at("chapter 1", 0L)
        timer.stopAtEndOfSegment()

        // Each tick moves the position twice as far as the clock
        at("chapter 1", 178_000L)
        wait(TICK_MS)
        at("chapter 1", 179_000L)
        wait(TICK_MS)
        at("chapter 2", 200L)
        wait(TICK_MS)

        target.commands shouldBe listOf("pause", "rewindToSegmentStart")
    }
}

private class FakeSleepTimerTarget : SleepTimerTarget {
    var sample = PlaybackSample(segmentId = null, positionMs = 0L, durationMs = 0L, isPlaying = false, hasNext = false)
    val commands = mutableListOf<String>()
    val fades = mutableListOf<Float>()

    override fun sample() = sample

    override fun pause() {
        commands += "pause"
        sample = sample.copy(isPlaying = false)
    }

    override fun rewindToSegmentStart() {
        commands += "rewindToSegmentStart"
    }

    override fun setFade(fade: Float) {
        fades += fade
    }
}
