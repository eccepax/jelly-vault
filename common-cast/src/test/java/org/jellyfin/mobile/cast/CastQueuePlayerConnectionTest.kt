package org.jellyfin.mobile.cast

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Sessions ending, going for a moment and coming back (see [CastQueuePlayerFixture]). */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
@Config(application = Application::class)
internal class CastQueuePlayerConnectionTest : CastQueuePlayerFixture() {

    @Test
    fun `when the cast ends, the queue and the place stay here, no further on`() {
        playing("a", "b")
        settle(20_000L)

        device.drop()
        val left = player.currentPosition
        settle(10_000L)

        left.near(20_000L + SETTLE_MS)
        player.currentPosition shouldBe left
        player.ids shouldBe listOf("a", "b")
        player.isPlaying shouldBe false
    }

    @Test
    fun `a session back after a moment without the network carries on as it was, without sending the queue again`() {
        playing("a", "b", "c")
        val connections = mutableListOf<Boolean>()
        player.onConnectionChange = { connections += it }
        val commands = device.commands.size

        device.suspend()
        settle(5_000L)
        device.resume()
        settle()

        connections shouldBe listOf(true)
        device.commands.size shouldBe commands
        player.isPlaying shouldBe true
        device.isPlaying shouldBe true
        player.currentPosition.near(5_000L + 2 * SETTLE_MS)
    }

    @Test
    fun `what changed here while the session was away goes once it's back, answers lost then too`() {
        playing("a", "b")

        player.seekTo(30_000L)
        device.suspend()
        player.pause()
        settle()
        device.resume()
        settle()

        device.isPlaying shouldBe false
        device.songPositionMs.near(30_000L)
        player.currentPosition.near(30_000L)
    }

    @Test
    fun `a command lost to a moment without the network isn't a failure, so the cast carries on once it's back`() {
        playing("a", "b", "c")
        val loads = device.commands.count { it.startsWith("load") }

        player.seekTo(30_000L)
        device.suspend(failAnswers = true)
        device.resume()
        settle()

        device.commands.count { it.startsWith("load") } shouldBe loads
        device.songPositionMs.near(30_000L + SETTLE_MS)

        // and it isn't counted towards the next one the cast device turns down
        device.failNext = 1
        player.seekTo(60_000L)
        settle()
        player.playerError.shouldBeNull()
        device.songPositionMs.near(60_000L + SETTLE_MS)
    }

    @Test
    fun `a cast that went quiet after the last song is left ended`() {
        playing("a", "b", index = 1)

        device.quiet = true
        settle(SONG_MS)
        device.drop()

        player.playbackState shouldBe Player.STATE_ENDED
        player.currentPosition shouldBe SONG_MS
    }

    @Test
    fun `a cast device that went quiet before the cast ended is left as far on as it would have got`() {
        playing("a", "b", "c")
        settle(90_000L)

        device.quiet = true
        settle(30_000L)
        device.drop()

        player.currentMediaItemIndex shouldBe 1
        player.currentPosition.near(20_000L + SETTLE_MS)
    }
}
