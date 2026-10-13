package org.jellyfin.mobile.music.playback

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.music.domain.PlayQueue
import org.jellyfin.mobile.music.domain.track
import org.jellyfin.mobile.music.playback.SleepTimer.Companion.FADE_MS
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {

    private val playback = FakePlaybackController()
    private val minute = 60_000L

    private fun TestScope.timer() = SleepTimer(playback, backgroundScope).also { runCurrent() }

    private fun TestScope.wait(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    private fun playing(vararg ids: String) {
        val tracks = ids.map { track(it).copy(durationMs = 180_000L) }
        playback.state.value = PlaybackState(queue = PlayQueue.of(tracks), isPlaying = true)
    }

    @Test
    fun `off until set`() = runTest {
        timer().state.value shouldBe SleepTimerState.Off
    }

    @Test
    fun `counts down, then pauses and turns itself off`() = runTest {
        val timer = timer()
        playing("a")

        timer.start(15 * minute)
        timer.state.value shouldBe SleepTimerState.Counting(remainingMs = 15 * minute)
        wait(5 * minute)
        timer.state.value shouldBe SleepTimerState.Counting(remainingMs = 10 * minute)

        wait(10 * minute)

        playback.commands shouldBe listOf("pause")
        timer.state.value shouldBe SleepTimerState.Off
    }

    @Test
    fun `the music fades out over the last seconds rather than cutting off`() = runTest {
        val timer = timer()
        playing("a")
        timer.start(minute)

        wait(minute - FADE_MS)
        playback.volumes.shouldBeEmpty()
        wait(FADE_MS / 2)
        playback.volumes.last() shouldBeLessThan 0.6f
        wait(FADE_MS / 2)

        playback.commands shouldBe listOf("pause")
        playback.volumes.last() shouldBe 1f
    }

    @Test
    fun `turning it off stops the countdown, and a fade under way is undone`() = runTest {
        val timer = timer()
        playing("a")
        timer.start(minute)
        wait(minute - FADE_MS / 2)

        timer.cancel()
        wait(minute)

        playback.commands.shouldBeEmpty()
        playback.volumes.last() shouldBe 1f
        timer.state.value shouldBe SleepTimerState.Off
    }

    @Test
    fun `more time adds fifteen minutes, undoing any fade`() = runTest {
        val timer = timer()
        playing("a")
        timer.start(minute)
        wait(minute - FADE_MS / 2)

        timer.extend()

        timer.state.value shouldBe SleepTimerState.Counting(remainingMs = FADE_MS / 2 + 15 * minute)
        playback.volumes.last() shouldBe 1f
    }

    @Test
    fun `choosing another time replaces the first`() = runTest {
        val timer = timer()
        playing("a")
        timer.start(minute)

        timer.start(30 * minute)
        wait(2 * minute)

        playback.commands.shouldBeEmpty()
        timer.state.value shouldBe SleepTimerState.Counting(remainingMs = 28 * minute)
    }

    @Test
    fun `end of song pauses when the song finishes`() = runTest {
        val timer = timer()
        playing("a", "b")
        timer.stopAtEndOfSong()
        timer.state.value shouldBe SleepTimerState.EndOfSong

        playback.positionMs = 179_500L
        wait(SleepTimer.TICK_MS)
        playback.state.value = playback.state.value.copy(queue = playback.state.value.queue.skipTo(1))
        playback.positionMs = 100L
        wait(SleepTimer.TICK_MS)

        playback.commands shouldBe listOf("pause", "seekTo 0")
        timer.state.value shouldBe SleepTimerState.Off
    }

    @Test
    fun `end of song keeps waiting when you skip to another song`() = runTest {
        val timer = timer()
        playing("a", "b")
        timer.stopAtEndOfSong()

        playback.positionMs = 30_000L
        wait(SleepTimer.TICK_MS)
        playback.state.value = playback.state.value.copy(queue = playback.state.value.queue.skipTo(1))
        playback.positionMs = 0L
        wait(SleepTimer.TICK_MS)

        playback.commands.shouldBeEmpty()
        timer.state.value shouldBe SleepTimerState.EndOfSong
    }

    @Test
    fun `end of song turns off when the last song ends by itself`() = runTest {
        val timer = timer()
        playing("a")
        timer.stopAtEndOfSong()

        playback.positionMs = 180_000L
        playback.state.value = playback.state.value.copy(isPlaying = false)
        wait(SleepTimer.TICK_MS)

        timer.state.value shouldBe SleepTimerState.Off
    }

    @Test
    fun `a fade on a levelled song starts from its levelled volume, and goes back to it`() = runTest {
        val volume = PlaybackVolume(playback)
        val timer = SleepTimer(playback, backgroundScope, volume).also { runCurrent() }
        playing("a")
        volume.setLevel(0.5f)
        timer.start(minute)

        wait(minute - FADE_MS / 2)
        playback.volumes.last() shouldBeLessThan 0.26f
        wait(FADE_MS / 2)

        playback.volumes.last() shouldBe 0.5f
    }
}
