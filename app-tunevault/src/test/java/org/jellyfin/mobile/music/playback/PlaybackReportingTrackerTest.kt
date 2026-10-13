package org.jellyfin.mobile.music.playback

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.music.domain.PlayQueue
import org.jellyfin.mobile.music.domain.track
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackReportingTrackerTest {

    private val playback = FakePlaybackController()
    private val reporter = FakePlaybackReporter()

    private val threeMinutes = track("a").copy(durationMs = 180_000L)
    private val tenMinutes = track("long").copy(durationMs = 600_000L)
    private val next = track("b").copy(durationMs = 180_000L)

    /**
     * Starts a stand-in player whose position advances 1 s per second while playing, then the tracker.
     * The player is launched first so on each shared second it moves before the tracker samples it.
     */
    private fun TestScope.startTracking(): PlaybackReportingTracker {
        backgroundScope.launch {
            while (true) {
                delay(1_000L)
                if (playback.state.value.isPlaying) playback.positionMs += 1_000L
            }
        }
        val tracker = PlaybackReportingTracker(playback, reporter, backgroundScope)
        tracker.start()
        runCurrent()
        return tracker
    }

    private fun play(vararg tracks: org.jellyfin.mobile.music.domain.Track, index: Int = 0) {
        playback.positionMs = 0L
        playback.state.value = PlaybackState(queue = PlayQueue.of(tracks.toList(), index), isPlaying = true)
    }

    private fun setPlaying(isPlaying: Boolean) {
        playback.state.value = playback.state.value.copy(isPlaying = isPlaying)
    }

    private fun skipTo(index: Int) {
        playback.positionMs = 0L
        playback.state.value = playback.state.value.copy(queue = playback.state.value.queue.skipTo(index))
    }

    private fun TestScope.listen(seconds: Int) {
        advanceTimeBy(seconds * 1_000L + 1)
        runCurrent()
    }

    @Test
    fun `nothing is reported before the listening threshold`() = runTest {
        startTracking()
        play(threeMinutes)

        listen(60)

        reporter.reports.shouldBeEmpty()
    }

    @Test
    fun `a track is reported once half of it has been listened to`() = runTest {
        startTracking()
        play(threeMinutes)

        listen(90)

        reporter.reports shouldBe listOf("started a @90s")
    }

    @Test
    fun `a long track is reported after four minutes`() = runTest {
        startTracking()
        play(tenMinutes)

        listen(239)
        reporter.reports.shouldBeEmpty()
        listen(1)

        reporter.reports shouldBe listOf("started long @240s")
    }

    @Test
    fun `a track skipped before the threshold is never reported`() = runTest {
        startTracking()
        play(threeMinutes, next)
        listen(30)

        skipTo(1)
        listen(30)

        reporter.reports.shouldBeEmpty()
    }

    @Test
    fun `paused time does not count as listening`() = runTest {
        startTracking()
        play(threeMinutes)
        listen(60)

        setPlaying(false)
        listen(600)
        reporter.reports.shouldBeEmpty()

        setPlaying(true)
        listen(30)
        reporter.reports shouldBe listOf("started a @90s")
    }

    @Test
    fun `seeking forward does not count as listening`() = runTest {
        startTracking()
        play(threeMinutes)
        listen(10)

        playback.positionMs = 170_000L
        listen(5)

        reporter.reports.shouldBeEmpty()
    }

    @Test
    fun `once reported, progress is sent every ten seconds`() = runTest {
        startTracking()
        play(threeMinutes)
        listen(90)

        listen(20)

        reporter.reports shouldBe listOf("started a @90s", "progress a @100s", "progress a @110s")
    }

    @Test
    fun `once reported, pausing and resuming are sent straight away`() = runTest {
        startTracking()
        play(threeMinutes)
        listen(90)

        setPlaying(false)
        runCurrent()
        setPlaying(true)
        runCurrent()

        reporter.reports shouldBe listOf("started a @90s", "progress a @90s paused", "progress a @90s")
    }

    @Test
    fun `moving on from a reported track reports where it stopped`() = runTest {
        startTracking()
        play(threeMinutes, next)
        listen(95)

        skipTo(1)
        runCurrent()

        reporter.reports shouldBe listOf("started a @90s", "stopped a @95s")
    }

    @Test
    fun `the next track starts counting from zero`() = runTest {
        startTracking()
        play(threeMinutes, next)
        listen(95)
        skipTo(1)

        listen(90)

        reporter.reports shouldBe listOf("started a @90s", "stopped a @95s", "started b @90s")
    }

    @Test
    fun `stopping playback reports the reported track as stopped`() = runTest {
        startTracking()
        play(threeMinutes)
        listen(95)

        playback.stop()
        runCurrent()

        reporter.reports shouldBe listOf("started a @90s", "stopped a @95s")
    }

    @Test
    fun `a repeat-one replay counts as another play`() = runTest {
        startTracking()
        play(threeMinutes)
        listen(179)

        playback.positionMs = 0L // the player looped back to the start of the same queue entry
        listen(90)

        reporter.reports.first() shouldBe "started a @90s"
        reporter.reports.filter { it.startsWith("stopped") } shouldBe listOf("stopped a @179s")
        reporter.reports.last() shouldBe "started a @90s"
    }

    @Test
    fun `restarting a track part way through is not a replay`() = runTest {
        startTracking()
        play(threeMinutes)
        listen(100)

        playback.positionMs = 0L // "previous" restarted the track
        listen(5)

        reporter.reports.filter { it.startsWith("stopped") }.shouldBeEmpty()
    }

    @Test
    fun `a failed report doesn't stop later ones`() = runTest {
        startTracking()
        play(threeMinutes, next)
        reporter.failNext = true
        listen(95)

        skipTo(1)
        runCurrent()

        reporter.reports shouldBe listOf("stopped a @95s")
    }

    @Test
    fun `finishing reports the playing track as stopped, and returns once it's sent`() = runTest {
        val tracker = startTracking()
        play(threeMinutes)
        listen(100)

        tracker.finish()

        reporter.reports.last() shouldBe "stopped a @100s"
    }

    @Test
    fun `finishing then stopping playback reports the stop once`() = runTest {
        val tracker = startTracking()
        play(threeMinutes)
        listen(100)

        tracker.finish()
        playback.state.value = PlaybackState()
        runCurrent()

        reporter.reports.count { it.startsWith("stopped") } shouldBe 1
    }

    @Test
    fun `finishing with nothing reported sends nothing`() = runTest {
        val tracker = startTracking()
        play(threeMinutes)
        listen(10)

        tracker.finish()

        reporter.reports.shouldBeEmpty()
    }

    @Test
    fun `finishing gives up waiting on a server that never answers`() = runTest {
        val tracker = startTracking()
        play(threeMinutes)
        listen(100)
        reporter.hang = true

        val startedAt = testScheduler.currentTime
        tracker.finish()

        (testScheduler.currentTime - startedAt) shouldBe PlaybackReportingTracker.FINISH_TIMEOUT_MS
    }
}
