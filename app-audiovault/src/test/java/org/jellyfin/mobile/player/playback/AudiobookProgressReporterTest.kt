package org.jellyfin.mobile.player.playback

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.player.playback.AudiobookProgressReporter.Companion.PROGRESS_INTERVAL_MS
import org.jellyfin.mobile.player.playback.AudiobookProgressReporter.Companion.SAMPLE_INTERVAL_MS
import org.junit.jupiter.api.Test

/**
 * Where the listener is, the resume position the whole app depends on: streamed tracks tell the
 * server as they play, downloaded ones keep it on the device for the server to hear about later.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudiobookProgressReporterTest {

    private val controller = FakeAudiobookController()
    private val server = RecordingSink()
    private val device = RecordingSink()

    private val streamed = AudiobookTrack(id = "streamed", title = "Streamed", durationMs = 3_600_000L)
    private val nextPart = AudiobookTrack(id = "next", title = "Next", durationMs = 3_600_000L)
    private val downloaded = streamed.copy(id = "downloaded", fromDevice = true)

    private fun TestScope.reporter() =
        AudiobookProgressReporter(controller, server, device, backgroundScope).also {
            it.start()
            runCurrent()
        }

    private fun TestScope.wait(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    /** The player moves on by [ms] while playing. */
    private fun TestScope.play(ms: Long) {
        var left = ms
        while (left > 0) {
            val step = minOf(left, SAMPLE_INTERVAL_MS)
            controller.positionMs += step
            wait(step)
            left -= step
        }
    }

    private fun TestScope.pause() {
        controller.pause()
        runCurrent()
    }

    // --- Streamed tracks ---------------------------------------------------------------------------

    @Test
    fun `starting a book tells the server where it started`() = runTest {
        reporter()

        controller.playing(streamed, positionMs = 754_000L)
        runCurrent()

        server.reports shouldBe listOf("started streamed at 754000")
    }

    @Test
    fun `while playing, the position goes to the server every ten seconds`() = runTest {
        reporter()
        controller.playing(streamed, positionMs = 0L)
        runCurrent()

        play(PROGRESS_INTERVAL_MS * 2)

        server.reports shouldBe listOf("started streamed at 0", "progress streamed at 10000", "progress streamed at 20000")
    }

    @Test
    fun `pausing tells the server where it paused, and the periodic reports stop`() = runTest {
        reporter()
        controller.playing(streamed, positionMs = 0L)
        runCurrent()
        play(4_000L)

        pause()
        wait(PROGRESS_INTERVAL_MS * 3)

        server.reports shouldBe listOf("started streamed at 0", "paused streamed at 4000")
    }

    @Test
    fun `stopping part way reports where the listener stopped`() = runTest {
        reporter()
        controller.playing(streamed, positionMs = 100_000L)
        runCurrent()
        play(3_000L)

        controller.stop()
        runCurrent()

        server.reports.last() shouldBe "stopped streamed at 103000"
    }

    @Test
    fun `when a track plays through, the server hears it finished and the next one started`() = runTest {
        reporter()
        controller.playing(streamed, nextPart, positionMs = 3_598_000L)
        runCurrent()
        play(2_000L)

        controller.state.value = controller.state.value.copy(currentIndex = 1)
        controller.positionMs = 0L
        runCurrent()

        server.reports shouldBe listOf("started streamed at 3598000", "finished streamed", "started next at 0")
    }

    @Test
    fun `skipping to another part early on reports where the first one was left, unfinished`() = runTest {
        reporter()
        controller.playing(streamed, nextPart, positionMs = 60_000L)
        runCurrent()

        controller.skipTo(1)
        runCurrent()

        server.reports shouldBe listOf("started streamed at 60000", "stopped streamed at 60000", "started next at 0")
    }

    @Test
    fun `when the last part ends, the book is finished`() = runTest {
        reporter()
        controller.playing(streamed, positionMs = 3_599_000L)
        runCurrent()

        controller.state.value = controller.state.value.copy(isEnded = true, isPlaying = false)
        controller.positionMs = 3_600_000L
        runCurrent()
        wait(PROGRESS_INTERVAL_MS)

        server.reports shouldBe listOf("started streamed at 3599000", "finished streamed")
    }

    @Test
    fun `playing a finished book again starts a new listen`() = runTest {
        reporter()
        controller.playing(streamed, positionMs = 3_599_000L)
        runCurrent()
        controller.state.value = controller.state.value.copy(isEnded = true, isPlaying = false)
        runCurrent()

        controller.positionMs = 0L
        controller.state.value = controller.state.value.copy(isEnded = false, isPlaying = true)
        runCurrent()

        server.reports.last() shouldBe "started streamed at 0"
    }

    @Test
    fun `played again in its last moments after finishing, a new listen starts`() = runTest {
        reporter()
        controller.playing(streamed, positionMs = 3_599_000L)
        runCurrent()
        controller.state.value = controller.state.value.copy(isEnded = true, isPlaying = false)
        runCurrent()

        controller.positionMs = 3_596_000L
        controller.state.value = controller.state.value.copy(isEnded = false, isPlaying = true)
        runCurrent()
        play(1_000L)
        pause()

        server.reports.takeLast(2) shouldBe listOf("started streamed at 3596000", "paused streamed at 3597000")
    }

    @Test
    fun `a playback error ends the listen, so the server doesn't keep a dead session`() = runTest {
        reporter()
        controller.playing(streamed, positionMs = 50_000L)
        runCurrent()

        controller.state.value = controller.state.value.copy(error = "No route to host", isPlaying = false, playWhenReady = false)
        runCurrent()

        server.reports.last() shouldBe "stopped streamed at 50000"
    }

    @Test
    fun `a report that fails doesn't stop the ones after it`() = runTest {
        server.failing = true
        reporter()
        controller.playing(streamed, positionMs = 0L)
        runCurrent()
        server.failing = false

        play(PROGRESS_INTERVAL_MS)

        server.reports shouldBe listOf("progress streamed at 10000")
    }

    @Test
    fun `signing out sends the last report before the token goes`() = runTest {
        val reporter = reporter()
        controller.playing(streamed, positionMs = 100_000L)
        runCurrent()
        play(2_000L)

        reporter.finishAndStop()

        server.reports.last() shouldBe "stopped streamed at 102000"
    }

    @Test
    fun `once signed out, the stop that follows isn't reported again`() = runTest {
        val reporter = reporter()
        controller.playing(streamed, positionMs = 100_000L)
        runCurrent()

        reporter.finishAndStop()
        runCurrent()

        server.reports shouldBe listOf("started streamed at 100000", "stopped streamed at 100000")
    }

    @Test
    fun `signing out stops the book only once where it got to is reported, as stopping loses it`() = runTest {
        // As through the app's MediaController: the position is gone the moment the book stops, and
        // the state only catches up later
        val mediaController = object : AudiobookPlaybackController by controller {
            override fun stop() {
                controller.commands += "stop"
                controller.positionMs = 0L
            }
        }
        val reporter = AudiobookProgressReporter(mediaController, server, device, backgroundScope)
        reporter.start()
        controller.playing(streamed, positionMs = 100_000L)
        runCurrent()

        reporter.finishAndStop()

        server.reports.last() shouldBe "stopped streamed at 100000"
        controller.commands shouldBe listOf("stop")
    }

    @Test
    fun `nothing is reported after signing out's last report, though the book takes a moment to stop`() = runTest {
        // As through the app's MediaController: the book goes on a moment before its state says it stopped
        val mediaController = object : AudiobookPlaybackController by controller {
            override fun stop() {
                controller.commands += "stop"
            }
        }
        val reporter = AudiobookProgressReporter(mediaController, server, device, backgroundScope)
        reporter.start()
        controller.playing(streamed, positionMs = 100_000L)
        runCurrent()

        reporter.finishAndStop()
        controller.pause()
        play(PROGRESS_INTERVAL_MS)
        controller.state.value = AudiobookPlaybackState()
        runCurrent()

        server.reports shouldBe listOf("started streamed at 100000", "stopped streamed at 100000")
    }

    @Test
    fun `after signing out, the next book is reported again`() = runTest {
        val reporter = reporter()
        controller.playing(streamed, positionMs = 100_000L)
        runCurrent()
        reporter.finishAndStop()
        runCurrent()

        controller.playing(nextPart, positionMs = 0L)
        runCurrent()

        server.reports.last() shouldBe "started next at 0"
    }

    // --- Downloaded tracks --------------------------------------------------------------------------

    @Test
    fun `a downloaded track's listening is kept on the device, not sent to the server`() = runTest {
        reporter()
        controller.playing(downloaded, positionMs = 0L)
        runCurrent()

        play(PROGRESS_INTERVAL_MS)
        pause()

        server.reports.shouldBeEmpty()
        device.reports shouldBe listOf("started downloaded at 0", "progress downloaded at 10000", "paused downloaded at 10000")
    }

    @Test
    fun `a downloaded track playing on a cast device is streamed there, so the server hears of it`() = runTest {
        reporter()
        controller.playing(downloaded, positionMs = 0L, isRemote = true)
        runCurrent()

        play(PROGRESS_INTERVAL_MS)
        pause()

        device.reports.shouldBeEmpty()
        server.reports shouldBe listOf("started downloaded at 0", "progress downloaded at 10000", "paused downloaded at 10000")
    }

    @Test
    fun `a download cast part way ends its listen here, and the server hears the rest from there`() = runTest {
        reporter()
        controller.playing(downloaded, positionMs = 0L)
        runCurrent()
        play(5_000L)

        cast(true)
        play(PROGRESS_INTERVAL_MS)

        device.reports shouldBe listOf("started downloaded at 0", "stopped downloaded at 5000")
        server.reports shouldBe listOf("started downloaded at 5000", "progress downloaded at 15000")
    }

    @Test
    fun `a download back from a cast device ends its listen on the server, and goes on here`() = runTest {
        reporter()
        controller.playing(downloaded, positionMs = 0L, isRemote = true)
        runCurrent()
        play(5_000L)

        cast(false)
        pause()

        server.reports shouldBe listOf("started downloaded at 0", "stopped downloaded at 5000")
        device.reports shouldBe listOf("started downloaded at 5000", "paused downloaded at 5000")
    }

    @Test
    fun `a download back from a cast device ends and goes on where it was, before the player here has got there`() =
        runTest {
            reporter()
            controller.playing(downloaded, positionMs = 0L, isRemote = true)
            runCurrent()
            play(5_000L)

            // This device's player has the track, and not yet the place it was given
            controller.positionMs = 0L
            cast(false)

            server.reports shouldBe listOf("started downloaded at 0", "stopped downloaded at 5000")
            device.reports shouldBe listOf("started downloaded at 5000")
        }

    @Test
    fun `a streamed track cast part way goes on as one listen on the server`() = runTest {
        reporter()
        controller.playing(streamed, positionMs = 0L)
        runCurrent()
        play(5_000L)

        cast(true)
        pause()

        server.reports shouldBe listOf("started streamed at 0", "paused streamed at 5000")
    }

    @Test
    fun `a streamed track back from a cast device, paused, keeps its place before the player here has got there`() =
        runTest {
            reporter()
            controller.playing(streamed, positionMs = 2_700_000L, isRemote = true)
            runCurrent()
            play(5_000L)

            // Back here paused, in the same change, and this device's player not yet at the place
            controller.positionMs = 0L
            controller.state.value =
                controller.state.value.copy(isRemote = false, playWhenReady = false, isPlaying = false)
            runCurrent()

            server.reports shouldBe listOf("started streamed at 2700000", "paused streamed at 2705000")
        }

    @Test
    fun `a download finished on a cast device stays finished back here, paused at its end`() = runTest {
        reporter()
        controller.playing(downloaded, positionMs = 3_599_000L, isRemote = true)
        runCurrent()
        controller.positionMs = 3_600_000L
        controller.state.value = controller.state.value.copy(isEnded = true, isPlaying = false)
        runCurrent()

        controller.state.value =
            controller.state.value.copy(isEnded = false, isRemote = false, playWhenReady = false)
        runCurrent()
        wait(PROGRESS_INTERVAL_MS)

        server.reports shouldBe listOf("started downloaded at 3599000", "finished downloaded")
        device.reports.shouldBeEmpty()
    }

    @Test
    fun `played again from its start after finishing on a cast device, a new listen starts here`() = runTest {
        reporter()
        controller.playing(downloaded, positionMs = 3_599_000L, isRemote = true)
        runCurrent()
        controller.positionMs = 3_600_000L
        controller.state.value = controller.state.value.copy(isEnded = true, isPlaying = false)
        runCurrent()
        controller.state.value =
            controller.state.value.copy(isEnded = false, isRemote = false, playWhenReady = false)
        runCurrent()

        // A seek back says nothing new of the state: the position alone moved
        controller.positionMs = 0L
        wait(SAMPLE_INTERVAL_MS)

        device.reports shouldBe listOf("started downloaded at 0")
    }

    @Test
    fun `a download that ends just as the cast does is finished where it played, not started again here`() = runTest {
        reporter()
        controller.playing(downloaded, positionMs = 3_599_000L, isRemote = true)
        runCurrent()

        controller.positionMs = 3_600_000L
        controller.state.value = controller.state.value.copy(isEnded = true, isPlaying = false, isRemote = false)
        runCurrent()
        wait(PROGRESS_INTERVAL_MS)

        server.reports shouldBe listOf("started downloaded at 3599000", "finished downloaded")
        device.reports.shouldBeEmpty()
    }

    @Test
    fun `a download that fails just as the cast ends is stopped where it played`() = runTest {
        reporter()
        controller.playing(downloaded, positionMs = 50_000L, isRemote = true)
        runCurrent()

        controller.state.value = controller.state.value.copy(
            error = "Cast session ended",
            isPlaying = false,
            playWhenReady = false,
            isRemote = false,
        )
        runCurrent()

        server.reports shouldBe listOf("started downloaded at 50000", "stopped downloaded at 50000")
        device.reports.shouldBeEmpty()
    }

    /** Playback moves onto a cast device, or back here, where it was. */
    private fun TestScope.cast(remote: Boolean) {
        controller.state.value = controller.state.value.copy(isRemote = remote)
        runCurrent()
    }

    @Test
    fun `finishing a downloaded track keeps it as finished`() = runTest {
        reporter()
        controller.playing(downloaded, positionMs = 3_599_000L)
        runCurrent()

        controller.state.value = controller.state.value.copy(isEnded = true, isPlaying = false)
        runCurrent()

        device.reports.last() shouldBe "finished downloaded"
    }

    /** Records reports as text; [failing] makes each one throw, as when the server is out of reach. */
    private class RecordingSink : AudiobookProgressSink {
        val reports = mutableListOf<String>()
        var failing = false

        private fun record(report: String) {
            if (failing) error("No route to host")
            reports += report
        }

        override suspend fun started(track: AudiobookTrack, positionMs: Long) = record(
            "started ${track.id} at $positionMs",
        )

        override suspend fun progress(track: AudiobookTrack, positionMs: Long, isPaused: Boolean) =
            record("${if (isPaused) "paused" else "progress"} ${track.id} at $positionMs")

        override suspend fun stopped(track: AudiobookTrack, positionMs: Long) = record(
            "stopped ${track.id} at $positionMs",
        )

        override suspend fun finished(track: AudiobookTrack) = record("finished ${track.id}")
    }
}
