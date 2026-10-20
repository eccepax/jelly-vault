package org.jellyfin.mobile.player.playback

import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeferredAudiobookControllerTest {

    private val connection = SettableFuture.create<FakeAudiobookController>()
    private val scope = TestScope(UnconfinedTestDispatcher())
    private val controller = DeferredAudiobookController(connection, MoreExecutors.directExecutor(), scope)

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
}
