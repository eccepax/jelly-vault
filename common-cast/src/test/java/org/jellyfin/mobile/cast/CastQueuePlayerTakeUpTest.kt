package org.jellyfin.mobile.cast

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.cast.CastDevice.Repeat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Another of this app's players taking up what's on the cast device (see [CastQueuePlayerFixture]). */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
@Config(application = Application::class)
internal class CastQueuePlayerTakeUpTest : CastQueuePlayerFixture() {

    @Test
    fun `another player of this app's can take up what's on the cast device`() {
        playing("a", "b", "c", "d", index = 1)
        player.release()
        val next = CastQueuePlayer(device, sources)

        next.takeUpDevice()

        next.ids shouldBe listOf("b", "c", "d")
        next.currentMediaItemIndex shouldBe 0
        next.isPlaying shouldBe true
        next.release()
    }

    @Test
    fun `a queue taken up keeps where its transcodes start, so its place is still the song's`() {
        transcodes = setOf("b")
        playing("a", "b", "c", index = 1, positionMs = 30_000L)
        player.release()
        val next = CastQueuePlayer(device, sources)

        next.takeUpDevice()

        next.currentMediaItem?.mediaId shouldBe "b"
        next.currentPosition.near(30_000L)
        next.release()
    }

    @Test
    fun `a queue taken up keeps the cast device's repeat and speed`() {
        playing("a", "b")
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.playbackParameters = PlaybackParameters(1.5f)
        settle()
        player.release()
        val next = CastQueuePlayer(device, sources)

        next.takeUpDevice()
        settle()

        next.repeatMode shouldBe Player.REPEAT_MODE_ALL
        next.playbackParameters.speed shouldBe 1.5f
        device.repeat shouldBe Repeat.ALL
        device.speed shouldBe 1.5f
        next.release()
    }

    @Test
    fun `a paused cast device taken up while it buffers (a seek on its remote) stays paused`() {
        playing("a")
        device.pressPause()
        settle()
        player.release()
        device.seekOnRemote(50_000L)
        val next = CastQueuePlayer(device, sources)

        next.takeUpDevice()
        settle()

        next.playWhenReady shouldBe false
        device.isPlaying shouldBe false
        next.release()
    }

    @Test
    fun `taking up waits until the cast device has said what all its items are`() {
        playing("a", "b", "c")
        player.release()
        val next = CastQueuePlayer(device, sources)
        device.queueUnknown = true
        device.sayAgain()

        next.takeUpDevice()
        next.mediaItemCount shouldBe 0
        device.queueUnknown = false
        device.sayAgain()

        next.ids shouldBe listOf("a", "b", "c")
        next.release()
    }

    @Test
    fun `a taken-up song seeked in finds its source then, and its stream starts there`() {
        transcodes = setOf("b")
        playing("b", "c", positionMs = 30_000L)
        player.release()
        val next = CastQueuePlayer(device, sources)
        next.takeUpDevice()
        asked.clear()

        next.seekTo(60_000L)
        settle()

        asked shouldContain ("b" to 60_000L)
        device.songPositionMs.near(60_000L)
        next.release()
    }
}
