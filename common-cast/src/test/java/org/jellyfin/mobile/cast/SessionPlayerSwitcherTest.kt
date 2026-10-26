package org.jellyfin.mobile.cast

import android.app.Application
import android.os.Looper
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Moving playback between this device and a cast device. A second ExoPlayer stands in for the cast
 * player: the switcher only sees a Player. Items here last 100 s.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SessionPlayerSwitcherTest {

    private fun newPlayer(): ExoPlayer = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
        .setMediaSourceFactory(FakeAudioSourceFactory())
        .build()

    private val local = newPlayer()
    private val remote = newPlayer()
    private var now = 0L
    private val sessionPlayers = mutableListOf<Player>()
    private val switcher = SessionPlayerSwitcher(local, now = { now }, setSessionPlayer = { sessionPlayers += it })

    @After
    fun tearDown() {
        local.release()
        remote.release()
    }

    private fun items(vararg ids: String) = ids.map {
        MediaItem.Builder().setMediaId(it).setUri("https://vault.test/$it").build()
    }

    private fun Player.load(index: Int, positionMs: Long, play: Boolean) {
        setMediaItems(items("a", "b", "c"), index, positionMs)
        playWhenReady = play
        prepare()
        settle()
    }

    private fun settle() {
        advance(local).untilPendingCommandsAreFullyHandled()
        advance(remote).untilPendingCommandsAreFullyHandled()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private val Player.ids get() = (0 until mediaItemCount).map { getMediaItemAt(it).mediaId }

    @Test
    fun `casting carries the queue, the place and play or pause over`() {
        local.load(index = 1, positionMs = 30_000L, play = false)

        switcher.switchTo(remote)
        settle()

        remote.ids shouldBe listOf("a", "b", "c")
        remote.currentMediaItemIndex shouldBe 1
        remote.currentPosition shouldBe 30_000L
        remote.playWhenReady shouldBe false
    }

    @Test
    fun `casting carries repeat and speed over`() {
        local.load(index = 0, positionMs = 0L, play = false)
        local.repeatMode = Player.REPEAT_MODE_ALL
        local.setPlaybackSpeed(1.5f)
        settle()

        switcher.switchTo(remote)
        settle()

        remote.repeatMode shouldBe Player.REPEAT_MODE_ALL
        remote.playbackParameters.speed shouldBe 1.5f
    }

    @Test
    fun `playing here keeps playing on the cast device, and stops here`() {
        local.load(index = 0, positionMs = 10_000L, play = true)

        switcher.switchTo(remote)
        settle()

        remote.playWhenReady shouldBe true
        local.isPlaying shouldBe false
        local.playbackState shouldBe Player.STATE_IDLE
    }

    @Test
    fun `the session gets the new player`() {
        local.load(index = 0, positionMs = 0L, play = false)

        switcher.switchTo(remote)

        sessionPlayers.single() shouldBeSameInstanceAs remote
        switcher.player shouldBeSameInstanceAs remote
    }

    @Test
    fun `the session gets the new player wrapped the way the app wraps its player`() {
        val wrapping = SessionPlayerSwitcher(local, wrap = ::Wrapped, setSessionPlayer = { sessionPlayers += it })

        wrapping.switchTo(remote)

        sessionPlayers.single().shouldBeInstanceOf<Wrapped>().wrappedPlayer shouldBeSameInstanceAs remote
    }

    @Test
    fun `switching to the player already playing does nothing`() {
        switcher.switchTo(local)

        sessionPlayers shouldBe emptyList()
    }

    @Test
    fun `ending the cast picks up here where the cast device was`() {
        local.load(index = 0, positionMs = 0L, play = false)
        switcher.switchTo(remote)
        settle()
        remote.seekTo(2, 40_000L)
        settle()

        switcher.switchTo(local)
        settle()

        local.currentMediaItemIndex shouldBe 2
        local.currentPosition shouldBe 40_000L
    }

    @Test
    fun `a cast device that has already let go is picked up from where it was last seen`() {
        local.load(index = 0, positionMs = 0L, play = false)
        switcher.switchTo(remote)
        settle()
        remote.seekTo(1, 20_000L)
        settle()
        // The cast session ending empties the cast player before the switch back
        remote.clearMediaItems()
        settle()

        switcher.switchTo(local)
        settle()

        local.ids shouldBe listOf("a", "b", "c")
        local.currentMediaItemIndex shouldBe 1
        local.currentPosition shouldBe 20_000L
    }

    @Test
    fun `a cast device that let go while playing is picked up as far on as it got`() {
        local.load(index = 0, positionMs = 0L, play = false)
        switcher.switchTo(remote)
        settle()
        remote.seekTo(1, 20_000L)
        remote.pause()
        settle()
        remote.play()
        settle()
        val seenAt = remote.currentPosition
        remote.clearMediaItems()
        settle()
        now += 5_000L

        switcher.switchTo(local)
        settle()

        local.currentMediaItemIndex shouldBe 1
        local.currentPosition shouldBe seenAt + 5_000L
        local.playWhenReady shouldBe true
    }

    private class Wrapped(player: Player) : ForwardingPlayer(player)
}
