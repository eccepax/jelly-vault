package org.jellyfin.mobile.player.playback

import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeferredAudiobookControllerTest {

    /** Each connection asked for, in order; the test completes them. */
    private val attempts = mutableListOf<SettableFuture<FakeAudiobookController>>()
    private val connection get() = attempts.last()
    private val scope = TestScope(UnconfinedTestDispatcher())
    private val controller = DeferredAudiobookController(
        connect = { SettableFuture.create<FakeAudiobookController>().also { attempts += it } },
        callbackExecutor = MoreExecutors.directExecutor(),
        scope = scope,
    )

    private val book = listOf(AudiobookTrack(id = "a", title = "a", durationMs = 0L))

    @Test
    fun `a book started before the connection plays once it's up`() {
        controller.play(book, startIndex = 0, startPositionMs = 42_000L)
        controller.pause()
        val delegate = FakeAudiobookController()

        connection.set(delegate)

        delegate.commands shouldBe listOf("play a from 0 at 42000", "pause")
    }

    @Test
    fun `commands after the connection go straight through`() {
        val delegate = FakeAudiobookController()
        controller.connect()
        connection.set(delegate)

        controller.togglePlayPause()
        controller.seekTo(5_000L)
        controller.rewind()
        controller.fastForward()
        controller.previousChapter()
        controller.nextChapter()
        controller.skipTo(2)
        controller.setSpeed(1.5f)
        controller.setSkipSilence(true)
        controller.stop()

        delegate.commands shouldBe listOf(
            "play", "seekTo 5000", "rewind", "fastForward", "previousChapter", "nextChapter", "skipTo 2",
            "speed 1.5", "skipSilence true", "stop",
        )
    }

    @Test
    fun `state, position and length mirror the connected controller`() {
        val delegate = FakeAudiobookController()
        controller.connect()
        connection.set(delegate)

        delegate.playing(AudiobookTrack(id = "a", title = "A", durationMs = 60_000L), positionMs = 1_234L)

        controller.state.value.currentTrack?.title shouldBe "A"
        controller.positionMs shouldBe 1_234L
        controller.durationMs shouldBe 60_000L
    }

    @Test
    fun `until it's connected nothing is playing, at full volume`() {
        controller.state.value shouldBe AudiobookPlaybackState()
        controller.positionMs shouldBe 0L
        controller.volume shouldBe 1f
    }

    /** Connecting starts the playback service and its player: not worth it until there's something to play. */
    @Test
    fun `following its state and position doesn't connect`() {
        controller.state.value
        controller.positionMs
        controller.durationMs

        attempts.shouldBeEmpty()
    }

    /** Signing out with nothing loaded, or a setting changed, mustn't start the service and its player. */
    @Test
    fun `with nothing playing, only starting a book connects`() {
        controller.stop()
        controller.pause()
        controller.play()
        controller.togglePlayPause()
        controller.seekTo(5_000L)
        controller.rewind()
        controller.fastForward()
        controller.previousChapter()
        controller.nextChapter()
        controller.skipTo(1)
        controller.setSpeed(1.5f)
        controller.setSkipSilence(true)
        controller.setVolume(0.5f)
        attempts.shouldBeEmpty()

        controller.play(book, startIndex = 0, startPositionMs = 0L)

        attempts.size shouldBe 1
    }

    @Test
    fun `when the connection is lost nothing is playing, and the next book started connects again`() {
        val lost = FakeAudiobookController()
        controller.connect()
        connection.set(lost)
        lost.playing(AudiobookTrack(id = "a", title = "A", durationMs = 60_000L), positionMs = 1_234L)

        controller.disconnected()

        controller.state.value shouldBe AudiobookPlaybackState()
        controller.positionMs shouldBe 0L
        lost.playing(AudiobookTrack(id = "b", title = "B", durationMs = 60_000L))
        controller.state.value shouldBe AudiobookPlaybackState()

        controller.play(book, startIndex = 0, startPositionMs = 0L)
        val delegate = FakeAudiobookController()
        connection.set(delegate)

        attempts.size shouldBe 2
        delegate.commands shouldBe listOf("play a from 0 at 0")
    }
}
