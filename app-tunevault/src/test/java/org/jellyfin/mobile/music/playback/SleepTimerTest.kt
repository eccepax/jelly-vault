package org.jellyfin.mobile.music.playback

import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.common.playback.SleepTimer
import org.jellyfin.mobile.common.playback.SleepTimer.Companion.FADE_MS
import org.jellyfin.mobile.common.playback.SleepTimerState
import org.jellyfin.mobile.music.domain.PlayQueue
import org.jellyfin.mobile.music.domain.track
import org.junit.jupiter.api.Test

/**
 * The shared sleep timer driving TuneVault's player, through [PlaybackSleepTimerTarget]. How the
 * timer itself behaves is common's SleepTimerTest; these check what TuneVault's player gives it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {

    private val playback = FakePlaybackController()
    private val minute = 60_000L

    private fun TestScope.timer(volume: PlaybackVolume = PlaybackVolume(playback)) =
        SleepTimer(PlaybackSleepTimerTarget(playback, volume), backgroundScope).also { runCurrent() }

    private fun TestScope.wait(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    private fun playing(vararg ids: String, durationMs: Long = 180_000L) {
        val tracks = ids.map { track(it).copy(durationMs = durationMs) }
        playback.state.value = PlaybackState(queue = PlayQueue.of(tracks), isPlaying = true)
    }

    private fun skipToNextSong() {
        playback.state.value = playback.state.value.copy(queue = playback.state.value.queue.skipTo(1))
        playback.positionMs = 100L
    }

    @Test
    fun `end of song pauses when the song finishes, back at the start of the next`() = runTest {
        val timer = timer()
        playing("a", "b")
        timer.stopAtEndOfSegment()

        playback.positionMs = 179_500L
        wait(SleepTimer.TICK_MS)
        skipToNextSong()
        wait(SleepTimer.TICK_MS)

        playback.commands shouldBe listOf("pause", "seekTo 0")
        timer.state.value shouldBe SleepTimerState.Off
    }

    @Test
    fun `end of song goes by the stream's length when it differs from the track's listed one`() = runTest {
        val timer = timer()
        playing("a", "b", durationMs = 185_000L)
        playback.durationMs = 181_000L
        timer.stopAtEndOfSegment()

        playback.positionMs = 180_500L
        wait(SleepTimer.TICK_MS)
        skipToNextSong()
        wait(SleepTimer.TICK_MS)

        playback.commands shouldBe listOf("pause", "seekTo 0")
    }

    @Test
    fun `end of song goes by the track's listed length until the stream's is known`() = runTest {
        val timer = timer()
        playing("a", "b")
        playback.durationMs = 0L
        timer.stopAtEndOfSegment()

        playback.positionMs = 179_500L
        wait(SleepTimer.TICK_MS)
        skipToNextSong()
        wait(SleepTimer.TICK_MS)

        playback.commands shouldBe listOf("pause", "seekTo 0")
    }

    @Test
    fun `end of song turns off when the last song ends by itself`() = runTest {
        val timer = timer()
        playing("a")
        timer.stopAtEndOfSegment()

        playback.positionMs = 180_000L
        playback.state.value = playback.state.value.copy(isPlaying = false)
        wait(SleepTimer.TICK_MS)

        timer.state.value shouldBe SleepTimerState.Off
    }

    @Test
    fun `end of song turns off when the queue is cleared`() = runTest {
        val timer = timer()
        playing("a", "b")
        timer.stopAtEndOfSegment()

        playback.state.value = PlaybackState()
        wait(SleepTimer.TICK_MS)

        timer.state.value shouldBe SleepTimerState.Off
    }

    @Test
    fun `a fade on a levelled song starts from its levelled volume, and goes back to it`() = runTest {
        val volume = PlaybackVolume(playback)
        val timer = timer(volume)
        playing("a")
        volume.setLevel(0.5f)
        timer.start(minute)

        wait(minute - FADE_MS / 2)
        playback.volumes.last() shouldBeLessThan 0.26f
        wait(FADE_MS / 2)

        playback.volumes.last() shouldBe 0.5f
    }
}
