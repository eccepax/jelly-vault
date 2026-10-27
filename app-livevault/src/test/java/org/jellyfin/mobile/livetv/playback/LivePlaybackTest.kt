package org.jellyfin.mobile.livetv.playback

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The rule under test: every live stream the server opens gets closed, whatever the viewer does
 * (stop, switch, zap, background, a failed start, a player error). The provider allows ONE
 * connection, so a leaked stream blocks every other channel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LivePlaybackTest {

    private val log = EventLog()
    private val streams = FakeLiveStreams(log)
    private val player = FakeLivePlayer(log)
    private val ledger = FakeStreamLedger()
    private val lineup = listOf("a", "b", "c", "d")

    private fun TestScope.playback() = LivePlayback(
        streams = streams,
        player = player,
        closer = StreamCloser(streams, ledger, scope = backgroundScope),
        scope = backgroundScope,
    )

    /** After everything settles, no stream is left open on the server except [expected]. */
    private fun assertOnlyOpen(vararg expected: String) {
        streams.openStreams shouldBe expected.toSet()
        ledger.all().map { it.liveStreamId }.toSet() shouldBe expected.toSet()
    }

    @Test
    fun `playing a channel opens its stream and plays it`() = runTest {
        val playback = playback()

        playback.play("a", lineup)
        runCurrent()

        player.playing shouldBe "http://vault/a/1.m3u8"
        playback.state.value shouldBe PlaybackState.Playing("a")
        log.events shouldContainExactly listOf("open:a", "player.play:http://vault/a/1.m3u8", "playing:ls-a-1")
    }

    @Test
    fun `the channel shows as tuning while the server opens it`() = runTest {
        val playback = playback()
        streams.gates["a"] = CompletableDeferred()

        playback.play("a", lineup)
        runCurrent()

        playback.state.value shouldBe PlaybackState.Tuning("a")
    }

    @Test
    fun `stopping stops the player and closes the stream`() = runTest {
        val playback = playback()
        playback.play("a", lineup)
        runCurrent()

        playback.stop()
        settle()

        player.playing shouldBe null
        playback.state.value shouldBe PlaybackState.Idle
        assertOnlyOpen()
    }

    @Test
    fun `switching channels closes the old stream before opening the new one`() = runTest {
        // One provider connection: the new channel can't open while the old one holds it.
        val playback = playback()
        playback.play("a", lineup)
        runCurrent()
        log.events.clear()

        playback.play("b", lineup)
        runCurrent()

        log.events shouldContainExactly listOf(
            "player.stop",
            "close:ls-a-1",
            "open:b",
            "player.play:http://vault/b/1.m3u8",
            "playing:ls-b-1",
        )
        assertOnlyOpen("ls-b-1")
    }

    @Test
    fun `a failed open shows the failure and leaves nothing open`() = runTest {
        val playback = playback()
        streams.failing += "a"

        playback.play("a", lineup)
        settle()

        playback.state.value shouldBe PlaybackState.Failed("a")
        player.playing shouldBe null
        assertOnlyOpen()
    }

    @Test
    fun `a stream that opens after the viewer moved on is closed, never played`() = runTest {
        val playback = playback()
        val gate = CompletableDeferred<Unit>()
        streams.gates["a"] = gate
        playback.play("a", lineup)
        runCurrent()

        playback.play("b", lineup)
        runCurrent()
        gate.complete(Unit)
        settle()

        log.events shouldNotContain "player.play:http://vault/a/1.m3u8"
        player.playing shouldBe "http://vault/b/1.m3u8"
        playback.state.value shouldBe PlaybackState.Playing("b")
        assertOnlyOpen("ls-b-1")
    }

    @Test
    fun `the next channel waits for the abandoned open to be closed`() = runTest {
        // Opening b while a's open is still in flight would need a second provider connection.
        val playback = playback()
        val gate = CompletableDeferred<Unit>()
        streams.gates["a"] = gate
        playback.play("a", lineup)
        runCurrent()

        playback.play("b", lineup)
        runCurrent()
        log.events shouldNotContain "open:b"

        gate.complete(Unit)
        settle()
        log.events.indexOf("close:ls-a-1") shouldBe log.events.indexOf("open:b") - 1
    }

    @Test
    fun `stopping while tuning closes the stream once it opens`() = runTest {
        val playback = playback()
        val gate = CompletableDeferred<Unit>()
        streams.gates["a"] = gate
        playback.play("a", lineup)
        runCurrent()

        playback.stop()
        gate.complete(Unit)
        settle()

        player.playing shouldBe null
        playback.state.value shouldBe PlaybackState.Idle
        assertOnlyOpen()
    }

    @Test
    fun `a player error closes the stream and shows the failure`() = runTest {
        val playback = playback()
        playback.play("a", lineup)
        runCurrent()

        player.listener!!.onError()
        settle()

        playback.state.value shouldBe PlaybackState.Failed("a")
        player.playing shouldBe null
        assertOnlyOpen()
    }

    @Test
    fun `retrying a failed channel tunes it again`() = runTest {
        val playback = playback()
        streams.failing += "a"
        playback.play("a", lineup)
        settle()

        streams.failing.clear()
        playback.retry()
        settle()

        playback.state.value shouldBe PlaybackState.Playing("a")
    }

    @Test
    fun `progress is reported while playing, and not after stopping`() = runTest {
        val playback = playback()
        playback.play("a", lineup)
        runCurrent()

        advanceTimeBy(LivePlayback.PROGRESS_INTERVAL_MS * 2 + 1)
        log.events.count { it == "playing:ls-a-1" } shouldBe 3

        playback.stop()
        advanceTimeBy(LivePlayback.PROGRESS_INTERVAL_MS * 3)
        log.events.count { it == "playing:ls-a-1" } shouldBe 3
    }

    @Test
    fun `zapping shows the target at once and tunes only where it lands`() = runTest {
        val playback = playback()
        playback.play("a", lineup)
        runCurrent()
        log.events.clear()

        playback.zap(+1)
        playback.zap(+1)
        runCurrent()
        playback.state.value shouldBe PlaybackState.Tuning("c")
        log.events shouldNotContain "open:b"

        settle()
        log.events.filter { it.startsWith("open:") } shouldContainExactly listOf("open:c")
        playback.state.value shouldBe PlaybackState.Playing("c")
        assertOnlyOpen("ls-c-1")
    }

    @Test
    fun `zapping stops the current channel right away`() = runTest {
        // Holding the provider connection while the viewer flicks through channels serves no one.
        val playback = playback()
        playback.play("a", lineup)
        runCurrent()

        playback.zap(+1)
        runCurrent()

        player.playing shouldBe null
        assertOnlyOpen()
    }

    @Test
    fun `zapping wraps around the lineup`() = runTest {
        val playback = playback()
        playback.play("a", lineup)
        runCurrent()

        playback.zap(-1)
        settle()

        playback.state.value shouldBe PlaybackState.Playing("d")
    }

    @Test
    fun `going to the background closes the stream and coming back tunes it again`() = runTest {
        val playback = playback()
        playback.play("a", lineup)
        runCurrent()

        playback.pause()
        settle()
        assertOnlyOpen()

        playback.resume()
        settle()
        playback.state.value shouldBe PlaybackState.Playing("a")
        assertOnlyOpen("ls-a-2")
    }

    @Test
    fun `coming back to a channel that had failed tries it again`() = runTest {
        val playback = playback()
        streams.failing += "a"
        playback.play("a", lineup)
        settle()
        playback.state.value shouldBe PlaybackState.Failed("a")
        streams.failing -= "a"

        playback.pause()
        playback.resume()
        settle()

        playback.state.value shouldBe PlaybackState.Playing("a")
        assertOnlyOpen("ls-a-1")
    }

    @Test
    fun `coming back after stopping plays nothing`() = runTest {
        val playback = playback()
        playback.play("a", lineup)
        runCurrent()
        playback.stop()
        settle()

        playback.pause()
        playback.resume()
        settle()

        playback.state.value shouldBe PlaybackState.Idle
        log.events.count { it == "open:a" } shouldBe 1
    }

    @Test
    fun `flicking through every channel leaves only the last one open`() = runTest {
        val playback = playback()
        streams.gates["a"] = CompletableDeferred()
        playback.play("a", lineup)
        runCurrent()
        playback.play("b", lineup)
        playback.zap(+1)
        playback.zap(+1)
        playback.play("a", lineup)
        streams.gates.remove("a")!!.complete(Unit)

        settle()

        playback.state.value shouldBe PlaybackState.Playing("a")
        assertOnlyOpen("ls-a-2")
        ledger.all().map { it.channelId } shouldContainExactly listOf("a")
    }

    @Test
    fun `nothing is open after stop even when closes fail at first`() = runTest {
        val playback = playback()
        playback.play("a", lineup)
        runCurrent()
        streams.closeFailures = 2

        playback.stop()
        settle()

        assertOnlyOpen()
    }

    @Test
    fun `releasing for good stops everything`() = runTest {
        val playback = playback()
        playback.play("a", lineup)
        runCurrent()

        playback.release()
        settle()

        player.listener shouldBe null
        assertOnlyOpen()
        ledger.all().shouldBeEmpty()
    }

    // Casting: the phone keeps owning the stream; the cast device only plays it.

    @Test
    fun `a channel watched while casting is opened for the cast device`() = runTest {
        val playback = playback()
        player.onCastDevice.value = true

        playback.play("a", lineup)
        runCurrent()

        log.events shouldContainExactly listOf("open-for-cast:a", "player.play:http://vault/a/1.m3u8", "playing:ls-a-1")
        playback.state.value shouldBe PlaybackState.Playing("a")
    }

    @Test
    fun `casting starting mid-channel closes its stream and opens it again for the cast device`() = runTest {
        // The cast device can't play what the phone does (the tuner's raw MPEG-TS), and the provider
        // allows one connection: the phone's stream must be closed before the cast one opens.
        val playback = playback()
        playback.play("a", lineup)
        runCurrent()
        log.events.clear()

        player.onCastDevice.value = true
        runCurrent()

        log.events shouldContainExactly listOf(
            "player.stop",
            "close:ls-a-1",
            "open-for-cast:a",
            "player.play:http://vault/a/2.m3u8",
            "playing:ls-a-2",
        )
        playback.state.value shouldBe PlaybackState.Playing("a")
        assertOnlyOpen("ls-a-2")
    }

    @Test
    fun `casting ending mid-channel tunes it again here`() = runTest {
        val playback = playback()
        player.onCastDevice.value = true
        playback.play("a", lineup)
        runCurrent()
        log.events.clear()

        player.onCastDevice.value = false
        runCurrent()

        log.events shouldContainExactly listOf(
            "player.stop",
            "close:ls-a-1",
            "open:a",
            "player.play:http://vault/a/2.m3u8",
            "playing:ls-a-2",
        )
        assertOnlyOpen("ls-a-2")
    }

    @Test
    fun `a channel that failed is tried again when casting starts`() = runTest {
        val playback = playback()
        streams.failing += "a"
        playback.play("a", lineup)
        settle()
        streams.failing -= "a"

        player.onCastDevice.value = true
        settle()

        playback.state.value shouldBe PlaybackState.Playing("a")
        log.events.last { it.startsWith("open") } shouldBe "open-for-cast:a"
    }

    @Test
    fun `casting starting with nothing on opens nothing`() = runTest {
        val playback = playback()
        playback.play("a", lineup)
        runCurrent()
        playback.stop()
        settle()
        log.events.clear()

        player.onCastDevice.value = true
        settle()

        log.events.shouldBeEmpty()
        playback.state.value shouldBe PlaybackState.Idle
    }

    @Test
    fun `going to the background while casting keeps the channel on the cast device`() = runTest {
        val playback = playback()
        player.onCastDevice.value = true
        playback.play("a", lineup)
        runCurrent()

        playback.pause()
        settle()

        playback.state.value shouldBe PlaybackState.Playing("a")
        assertOnlyOpen("ls-a-1")
    }

    @Test
    fun `coming back from the background while casting leaves the channel playing`() = runTest {
        val playback = playback()
        player.onCastDevice.value = true
        playback.play("a", lineup)
        runCurrent()
        playback.pause()
        settle()
        log.events.clear()

        playback.resume()
        settle()

        log.events.filter { it.startsWith("open") }.shouldBeEmpty()
        assertOnlyOpen("ls-a-1")
    }

    @Test
    fun `casting ending while in the background frees the stream rather than play in a pocket`() = runTest {
        val playback = playback()
        player.onCastDevice.value = true
        playback.play("a", lineup)
        runCurrent()
        playback.pause()

        player.onCastDevice.value = false
        settle()

        player.playing shouldBe null
        assertOnlyOpen()
    }

    @Test
    fun `coming back after casting ended in the background tunes the channel here`() = runTest {
        val playback = playback()
        player.onCastDevice.value = true
        playback.play("a", lineup)
        runCurrent()
        playback.pause()
        player.onCastDevice.value = false
        settle()

        playback.resume()
        settle()

        playback.state.value shouldBe PlaybackState.Playing("a")
        log.events.last { it.startsWith("open") } shouldBe "open:a"
        assertOnlyOpen("ls-a-2")
    }

    @Test
    fun `the player is told which channel it plays`() = runTest {
        val playback = playback()

        playback.play("b", lineup)
        runCurrent()

        player.channel shouldBe "b"
    }
}
