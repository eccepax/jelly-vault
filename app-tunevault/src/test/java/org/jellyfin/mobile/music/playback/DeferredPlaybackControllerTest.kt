package org.jellyfin.mobile.music.playback

import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.jellyfin.mobile.music.domain.tracks
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DeferredPlaybackControllerTest {

    private val connection = SettableFuture.create<FakePlaybackController>()
    private val scope = TestScope(UnconfinedTestDispatcher())
    private val controller = DeferredPlaybackController(connection, MoreExecutors.directExecutor(), scope)

    @Test
    fun `commands sent before the connection are replayed in order once it's up`() {
        controller.play(tracks("a", "b"), startIndex = 1)
        controller.togglePlayPause()
        val delegate = FakePlaybackController()

        connection.set(delegate)

        delegate.commands shouldBe listOf("play a,b from 1", "togglePlayPause")
    }

    @Test
    fun `commands after the connection go straight through`() {
        val delegate = FakePlaybackController()
        connection.set(delegate)

        controller.skipToNext()
        controller.seekTo(5_000L)
        controller.playNext(tracks("x"))
        controller.addToQueue(tracks("y"))
        controller.removeFromQueue(1)
        controller.moveInQueue(from = 2, to = 0)
        controller.skipTo(3)
        controller.stop()

        delegate.commands shouldBe listOf(
            "skipToNext", "seekTo 5000", "playNext x", "addToQueue y", "remove 1", "move 2 to 0", "skipTo 3", "stop",
        )
    }

    @Test
    fun `state mirrors the connected controller`() {
        val delegate = FakePlaybackController()
        connection.set(delegate)

        delegate.play(tracks("a"), startIndex = 0)

        controller.state.value.currentTrack?.id shouldBe "a"
        controller.state.value.isPlaying shouldBe true
    }

    @Test
    fun `position is zero until connected, then the delegate's`() {
        controller.positionMs shouldBe 0L

        val delegate = FakePlaybackController().apply {
            positionMs = 4_200L
            durationMs = 180_000L
        }
        connection.set(delegate)

        controller.positionMs shouldBe 4_200L
        controller.durationMs shouldBe 180_000L
    }

    @Test
    fun `a failed connection drops queued commands instead of crashing`() {
        controller.play(tracks("a"), startIndex = 0)

        connection.setException(IOException("service unavailable"))
        controller.skipToNext()

        controller.state.value.currentTrack shouldBe null
        controller.positionMs shouldBe 0L
    }
}
