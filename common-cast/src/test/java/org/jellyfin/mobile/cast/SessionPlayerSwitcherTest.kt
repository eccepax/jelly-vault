package org.jellyfin.mobile.cast

import android.app.Application
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeInRange
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
 * device, behind [Placeholders] (Media3's cast player knows only some of its items, and gives
 * MediaItem.EMPTY for the rest) and [RemotePlaylist], the way SessionCasting puts them. Items here
 * last 100 s.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
@Config(application = Application::class)
class SessionPlayerSwitcherTest {

    private fun newPlayer(): ExoPlayer = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
        .setMediaSourceFactory(FakeAudioSourceFactory())
        .build()

    private val local = newPlayer()
    private val remote = newPlayer()
    private val cast = RemotePlaylist(Placeholders(remote))
    private var now = 0L
    private val sessionPlayers = mutableListOf<Player>()
    private val switcher = SessionPlayerSwitcher(local, now = { now }, setSessionPlayer = { sessionPlayers += it })

    @After
    fun tearDown() {
        local.release()
        remote.release()
    }

    private fun items(vararg ids: String) = ids.map {
        MediaItem.Builder().setMediaId(it).setUri("https://vault.test/$it")
            .setMediaMetadata(MediaMetadata.Builder().setDurationMs(SONG_MS).build())
            .build()
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

        switcher.switchTo(cast)
        settle()

        remote.ids shouldBe listOf("a", "b", "c")
        remote.currentMediaItemIndex shouldBe 1
        remote.currentPosition shouldBe 30_000L
        remote.playWhenReady shouldBe false
    }

    @Test
    fun `a queue emptied while casting stays empty once the cast ends, rather than the one before coming back`() {
        local.load(index = 1, positionMs = 30_000L, play = true)
        switcher.switchTo(cast)
        settle()

        // As an app stops playback (signing out, say)
        cast.stop()
        cast.clearMediaItems()
        settle()
        switcher.switchTo(local)
        settle()

        local.mediaItemCount shouldBe 0
    }

    @Test
    fun `a new queue the cast device never takes up still replaces the one before, back here`() {
        local.load(index = 0, positionMs = 0L, play = true)
        switcher.switchTo(cast)
        settle()

        // The cast device has stopped (an error, say), so it never reports the queue sent next
        remote.stop()
        settle()
        cast.setMediaItems(items("x", "y"), 1, 0L)
        settle()
        switcher.switchTo(local)
        settle()

        local.ids shouldBe listOf("x", "y")
        local.currentMediaItemIndex shouldBe 1
    }

    @Test
    fun `a volume asked for while casting is this device's once playback is back, and not the cast device's`() {
        local.load(index = 0, positionMs = 0L, play = false)
        switcher.switchTo(cast)
        settle()

        // The sleep timer's fade, say
        cast.volume = 0.3f
        switcher.switchTo(local)
        settle()

        local.volume shouldBe 0.3f
        remote.volume shouldBe 1f
    }

    @Test
    fun `without a volume asked for while casting, this device's stays as it was, even after an earlier cast's`() {
        local.load(index = 0, positionMs = 0L, play = false)
        switcher.switchTo(cast)
        settle()
        cast.volume = 0.3f
        switcher.switchTo(local)
        settle()
        local.volume = 0.8f

        switcher.switchTo(cast)
        settle()
        switcher.switchTo(local)
        settle()

        local.volume shouldBe 0.8f
    }

    @Test
    fun `casting carries repeat and speed over`() {
        local.load(index = 0, positionMs = 0L, play = false)
        local.repeatMode = Player.REPEAT_MODE_ALL
        local.setPlaybackSpeed(1.5f)
        settle()

        switcher.switchTo(cast)
        settle()

        remote.repeatMode shouldBe Player.REPEAT_MODE_ALL
        remote.playbackParameters.speed shouldBe 1.5f
    }

    @Test
    fun `playing here keeps playing on the cast device, and stops here`() {
        local.load(index = 0, positionMs = 10_000L, play = true)

        switcher.switchTo(cast)
        settle()

        remote.playWhenReady shouldBe true
        local.isPlaying shouldBe false
        local.playbackState shouldBe Player.STATE_IDLE
    }

    @Test
    fun `the session gets the new player`() {
        local.load(index = 0, positionMs = 0L, play = false)

        switcher.switchTo(cast)

        sessionPlayers.single() shouldBeSameInstanceAs cast
        switcher.player shouldBeSameInstanceAs cast
    }

    @Test
    fun `the session gets the new player wrapped the way the app wraps its player`() {
        val wrapping = SessionPlayerSwitcher(local, wrap = ::Wrapped, setSessionPlayer = { sessionPlayers += it })

        wrapping.switchTo(cast)

        sessionPlayers.single().shouldBeInstanceOf<Wrapped>().wrappedPlayer shouldBeSameInstanceAs cast
    }

    @Test
    fun `switching to the player already playing does nothing`() {
        switcher.switchTo(local)

        sessionPlayers shouldBe emptyList()
    }

    @Test
    fun `ending the cast picks up here where the cast device was`() {
        local.load(index = 0, positionMs = 0L, play = false)
        switcher.switchTo(cast)
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
        switcher.switchTo(cast)
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
    fun `a cast device that let go while playing is picked up as far on as it got, paused here`() {
        local.load(index = 0, positionMs = 0L, play = false)
        switcher.switchTo(cast)
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
        local.currentPosition shouldTrailBy seenAt + 5_000L
        local.playWhenReady shouldBe false
    }

    /** Casts, then plays on the cast device from [index] at [positionMs] until it lets go, seen [laterMs] after. */
    private fun castDeviceLetGo(
        index: Int,
        positionMs: Long,
        laterMs: Long,
        speed: Float = 1f,
        repeatMode: Int = Player.REPEAT_MODE_OFF,
    ): Long {
        local.load(index = 0, positionMs = 0L, play = false)
        switcher.switchTo(cast)
        settle()
        remote.repeatMode = repeatMode
        remote.setPlaybackSpeed(speed)
        remote.seekTo(index, positionMs)
        remote.play()
        advance(remote).untilState(Player.STATE_READY)
        settle()
        val seenAt = remote.currentPosition
        remote.clearMediaItems()
        settle()
        now += laterMs
        return seenAt
    }

    /**
     * [expectedMs], or a little short of it: the cast device's playback thread runs on its own, so
     * it may move on between its last report (what the switcher picked up from) and the test
     * reading its position afterwards. Never past it.
     */
    private infix fun Long.shouldTrailBy(expectedMs: Long) = this shouldBeInRange (expectedMs - SLACK_MS)..expectedMs

    @Test
    fun `a cast device that let go while playing faster is picked up as far on as that got it`() {
        val seenAt = castDeviceLetGo(index = 1, positionMs = 20_000L, laterMs = 5_000L, speed = 2f)

        switcher.switchTo(local)
        settle()

        local.currentMediaItemIndex shouldBe 1
        local.currentPosition shouldTrailBy seenAt + 10_000L
    }

    @Test
    fun `a cast device that let go near a song's end is picked up in the songs after, not past the end`() {
        val seenAt = castDeviceLetGo(index = 0, positionMs = 95_000L, laterMs = 110_000L)

        switcher.switchTo(local)
        settle()

        // The rest of a, all of b, and on into c
        local.currentMediaItemIndex shouldBe 2
        local.currentPosition shouldTrailBy seenAt + 110_000L - 2 * SONG_MS
    }

    @Test
    fun `a cast device that let go after the last song had ended is picked up at its end`() {
        castDeviceLetGo(index = 2, positionMs = 95_000L, laterMs = 60_000L)

        switcher.switchTo(local)
        settle()

        local.currentMediaItemIndex shouldBe 2
        // ExoPlayer reads a place at the very end as its last millisecond
        local.currentPosition shouldBeGreaterThanOrEqual SONG_MS - 1
    }

    @Test
    fun `a cast device repeating a song is picked up in that song`() {
        val seenAt =
            castDeviceLetGo(index = 1, positionMs = 80_000L, laterMs = 30_000L, repeatMode = Player.REPEAT_MODE_ONE)

        switcher.switchTo(local)
        settle()

        local.currentMediaItemIndex shouldBe 1
        // Round again from the start of it (on a slow machine it may have gone round before it was seen)
        local.currentPosition shouldBe (seenAt + 30_000L) % SONG_MS
    }

    @Test
    fun `ending the cast picks up where the cast device was last seen, even if the cast player went back to the start`() {
        val resetting = Resetting(remote)
        val cast = RemotePlaylist(resetting)
        local.load(index = 0, positionMs = 0L, play = false)
        switcher.switchTo(cast)
        settle()
        remote.seekTo(2, 40_000L)
        settle()
        // As the session ends, the cast player can read back the start of the queue, saying nothing
        resetting.reset = true

        switcher.switchTo(local)
        settle()

        local.currentMediaItemIndex shouldBe 2
        local.currentPosition shouldBe 40_000L
    }

    @Test
    fun `a cast player that goes idle at the start of its queue as its session ends isn't where it was last seen`() {
        val resetting = Resetting(remote)
        val cast = RemotePlaylist(resetting)
        local.load(index = 0, positionMs = 0L, play = false)
        switcher.switchTo(cast)
        settle()
        remote.seekTo(2, 40_000L)
        settle()
        // As the session ends, the cast player stops and reads back the start of its queue, and says so
        resetting.reset = true
        remote.stop()
        settle()

        switcher.switchTo(local)
        settle()

        local.currentMediaItemIndex shouldBe 2
        local.currentPosition shouldBe 40_000L
    }

    @Test
    fun `back from casting, playback waits here rather than starting out loud`() {
        local.load(index = 0, positionMs = 0L, play = true)
        switcher.switchTo(cast)
        settle()

        switcher.switchTo(local)
        settle()

        local.playWhenReady shouldBe false
    }

    @Test
    fun `back from casting, this device plays the songs as they were sent, not the cast player's placeholders`() {
        local.load(index = 0, positionMs = 0L, play = false)
        switcher.switchTo(cast)
        settle()
        cast.addMediaItems(items("d"))
        settle()

        switcher.switchTo(local)
        settle()

        local.ids shouldBe listOf("a", "b", "c", "d")
        (0 until local.mediaItemCount).map { local.getMediaItemAt(it).localConfiguration?.uri.toString() } shouldBe
            listOf("https://vault.test/a", "https://vault.test/b", "https://vault.test/c", "https://vault.test/d")
    }

    @Test
    fun `songs a player can't say how to play are left out rather than crash this device's player`() {
        local.load(index = 0, positionMs = 0L, play = false)
        switcher.switchTo(Placeholders(remote))
        settle()

        switcher.switchTo(local)
        settle()

        local.mediaItemCount shouldBe 0
    }

    private class Wrapped(player: Player) : ForwardingPlayer(player)

    /** A cast player that, once [reset], reads back the start of its queue without telling anyone. */
    private class Resetting(player: Player) : ForwardingPlayer(player) {
        var reset = false

        override fun getCurrentMediaItemIndex(): Int = if (reset) 0 else super.getCurrentMediaItemIndex()

        override fun getCurrentPosition(): Long = if (reset) 0L else super.getCurrentPosition()
    }

    private companion object {
        /** How long every song lasts (see FakeAudioSourceFactory), as its metadata says. */
        const val SONG_MS = 100_000L

        /** How far the cast device may get between its last report and the test reading it (at 2x, twice its own time). */
        const val SLACK_MS = 1_000L
    }

    @Test
    fun `ending the cast while a long queue is still on its way picks up all of it here`() {
        val songs = (0 until 50).map { "s$it" }
        local.setMediaItems(items(*songs.toTypedArray()), 30, 5_000L)
        local.prepare()
        settle()

        switcher.switchTo(cast)
        switcher.switchTo(local)
        settle()

        local.ids shouldBe songs
        local.currentMediaItemIndex shouldBe 30
    }

    @Test
    fun `a cast device playing a queue this app didn't send (started while casting) is picked up from its own`() {
        // Its own items say how to play them: no placeholders
        val castOnly = RemotePlaylist(remote)
        remote.setMediaItems(items("a", "b", "c"))
        remote.repeatMode = Player.REPEAT_MODE_ALL
        remote.playWhenReady = true
        remote.prepare()
        settle()
        switcher.switchTo(castOnly)
        // Something for the switcher to see it by
        remote.seekTo(1_000L)
        settle()

        now += 4 * SONG_MS
        switcher.switchTo(local)
        settle()

        local.ids shouldBe listOf("a", "b", "c")
    }
}
