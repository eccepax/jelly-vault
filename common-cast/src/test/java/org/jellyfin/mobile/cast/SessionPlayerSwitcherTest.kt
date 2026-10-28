package org.jellyfin.mobile.cast

import android.app.Application
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.longs.shouldBeInRange
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * Moving playback between this device and a cast device: an ExoPlayer here, and a [CastQueuePlayer]
 * on a [FakeCastDevice], the way SessionCasting puts them. Songs last [SONG_MS].
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
@Config(application = Application::class)
class SessionPlayerSwitcherTest {

    private val local: ExoPlayer = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
        .setMediaSourceFactory(FakeAudioSourceFactory())
        .build()
    private val device = FakeCastDevice()
    private val sources = CastSourceFinder { item, found -> found(CastSource { stream(item, it) }) }
    private val cast = CastQueuePlayer(device, sources)
    private val sessionPlayers = mutableListOf<Player>()
    private val switcher = SessionPlayerSwitcher(local, setSessionPlayer = { sessionPlayers += it })

    init {
        device.connect()
    }

    @After
    fun tearDown() {
        local.release()
        cast.release()
    }

    @Suppress("UnusedParameter")
    private fun stream(item: MediaItem, fromMs: Long) = CastStream("https://cast.test/${item.mediaId}", "audio/flac")

    private fun items(vararg ids: String) = ids.map {
        MediaItem.Builder().setMediaId(it).setUri("https://vault.test/$it")
            .setMediaMetadata(MediaMetadata.Builder().setDurationMs(SONG_MS).build())
            .build()
    }

    private fun Player.load(index: Int, positionMs: Long, play: Boolean) {
        setMediaItems(items("a", "b", "c"), index, positionMs)
        playWhenReady = play
        prepare()
        settleHere()
    }

    /** Time goes on for the cast device. */
    private fun settle(ms: Long = SETTLE_MS) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    }

    /**
     * Until this device's player has done what it was told. Media3's helper for it runs what's
     * waiting on the main looper early, moving the clock on, so only while the cast device waits on nothing.
     */
    private fun settleHere() {
        advance(local).untilPendingCommandsAreFullyHandled()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private val Player.ids get() = (0 until mediaItemCount).map { getMediaItemAt(it).mediaId }

    private fun Long.near(expectedMs: Long) = shouldBeInRange(expectedMs - SLACK_MS..expectedMs + SLACK_MS)

    @Test
    fun `casting carries the queue, the place and play or pause over`() {
        local.load(index = 1, positionMs = 30_000L, play = false)

        switcher.switchTo(cast)
        settle()

        cast.ids shouldBe listOf("a", "b", "c")
        cast.currentMediaItemIndex shouldBe 1
        cast.currentPosition shouldBe 30_000L
        cast.playWhenReady shouldBe false
        device.queue.map { it.item.mediaId } shouldBe listOf("b", "c")
        device.songPositionMs shouldBe 30_000L
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
        device.drop()
        switcher.switchTo(local)
        settleHere()

        local.mediaItemCount shouldBe 0
    }

    @Test
    fun `a stopped queue comes back stopped`() {
        local.load(index = 1, positionMs = 30_000L, play = true)
        switcher.switchTo(cast)
        settle()

        cast.stop()
        settle()
        switcher.switchTo(local)
        settleHere()

        local.ids shouldBe listOf("a", "b", "c")
        local.playbackState shouldBe Player.STATE_IDLE
    }

    @Test
    fun `a new queue the cast device never takes up still replaces the one before, back here`() {
        local.load(index = 0, positionMs = 0L, play = true)
        switcher.switchTo(cast)
        settle()

        device.failNext = Int.MAX_VALUE
        cast.setMediaItems(items("x", "y"), 1, 0L)
        settle()
        device.drop()
        switcher.switchTo(local)
        settleHere()

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
        settleHere()

        local.volume shouldBe 0.3f
        device.volume shouldBe 10
    }

    @Test
    fun `without a volume asked for while casting, this device's stays as it was`() {
        local.load(index = 0, positionMs = 0L, play = false)
        local.volume = 0.8f

        switcher.switchTo(cast)
        settle()
        switcher.switchTo(local)
        settleHere()

        local.volume shouldBe 0.8f
    }

    @Test
    fun `casting carries repeat and speed over`() {
        local.load(index = 0, positionMs = 0L, play = false)
        local.repeatMode = Player.REPEAT_MODE_ALL
        local.playbackParameters = PlaybackParameters(1.5f)
        settleHere()

        switcher.switchTo(cast)
        settle()

        cast.repeatMode shouldBe Player.REPEAT_MODE_ALL
        device.speed shouldBe 1.5f
    }

    @Test
    fun `playing here keeps playing on the cast device, and stops here`() {
        local.load(index = 0, positionMs = 10_000L, play = true)

        switcher.switchTo(cast)
        settle()

        device.isPlaying shouldBe true
        cast.isPlaying shouldBe true
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
    fun `ending the cast picks up here where the cast device was, paused`() {
        local.load(index = 0, positionMs = 0L, play = true)
        switcher.switchTo(cast)
        settle()
        cast.seekTo(2, 40_000L)
        settle(10_000L)

        device.drop()
        switcher.switchTo(local)
        settleHere()

        local.currentMediaItemIndex shouldBe 2
        local.currentPosition.near(50_000L)
        local.playWhenReady shouldBe false
    }

    @Test
    fun `a cast device that went quiet near a song's end is picked up in the songs after`() {
        local.load(index = 0, positionMs = 95_000L, play = true)
        switcher.switchTo(cast)
        settle()

        device.quiet = true
        settle(110_000L)
        device.drop()
        switcher.switchTo(local)
        settleHere()

        // The rest of a, all of b, and on into c
        local.currentMediaItemIndex shouldBe 2
        local.currentPosition.near(95_000L + 110_000L + SETTLE_MS - 2 * SONG_MS)
    }

    @Test
    fun `back from casting, this device plays the songs as they were given, not as they were cast`() {
        local.load(index = 0, positionMs = 0L, play = false)
        switcher.switchTo(cast)
        settle()
        cast.addMediaItems(items("d"))
        settle()

        switcher.switchTo(local)
        settleHere()

        local.ids shouldBe listOf("a", "b", "c", "d")
        (0 until local.mediaItemCount).map { local.getMediaItemAt(it).localConfiguration?.uri.toString() } shouldBe
            listOf("https://vault.test/a", "https://vault.test/b", "https://vault.test/c", "https://vault.test/d")
    }

    @Test
    fun `songs a player can't say how to play carry over too, so the queue lines up with the app's, without a crash`() {
        switcher.switchTo(cast)
        cast.setMediaItems(items("a") + MediaItem.Builder().setMediaId("nowhere").build() + items("c"), 2, 0L)
        settle()

        switcher.switchTo(local)
        settleHere()

        local.ids shouldBe listOf("a", "nowhere", "c")
        local.currentMediaItemIndex shouldBe 2
        // ExoPlayer's own sources need one: playing it fails then, rather than taking it now
        local.getMediaItemAt(1).localConfiguration.shouldNotBeNull()
    }

    @Test
    fun `the place is read and set through the app's wrapper, which knows where this device's streams start`() {
        // As AudioVault's player, whose transcode started part way counts from where it started
        val offset = Offset(local, startMs = 60_000L)
        val wrapping = SessionPlayerSwitcher(
            local,
            wrap = { if (it === local) offset else it },
            setSessionPlayer = { sessionPlayers += it },
        )
        local.load(index = 1, positionMs = 10_000L, play = false)

        wrapping.switchTo(cast)
        settle()
        cast.currentPosition shouldBe 70_000L

        cast.seekTo(80_000L)
        settle()
        wrapping.switchTo(local)
        settleHere()

        offset.currentPosition.near(80_000L)
        local.currentPosition.near(20_000L)
    }

    @Test
    fun `a moment without the network leaves playback on the cast device`() {
        local.load(index = 0, positionMs = 0L, play = true)
        switcher.switchTo(cast)
        cast.onConnectionChange = { switcher.switchTo(if (it) cast else local) }
        settle()

        device.suspend()
        settle(5_000L)
        device.resume()
        settle()

        switcher.player shouldBeSameInstanceAs cast
        sessionPlayers.size shouldBe 1
        device.isPlaying shouldBe true
    }

    @Test
    fun `moving straight on to another cast device keeps playing there`() {
        local.load(index = 0, positionMs = 0L, play = true)
        switcher.switchTo(cast)
        settle()

        // Another device picked: this session ends, and the next comes up
        device.drop()
        switcher.switchTo(local)
        settleHere()
        device.connect()
        switcher.switchTo(cast)
        settle()

        cast.isPlaying shouldBe true
        device.isPlaying shouldBe true
    }

    @Test
    fun `casting again a while after, or after playing here meanwhile, starts as playback here is`() {
        local.load(index = 0, positionMs = 0L, play = true)
        switcher.switchTo(cast)
        settle()
        device.drop()
        switcher.switchTo(local)
        settleHere()

        settle(SessionPlayerSwitcher.CARRY_ON_WITHIN_MS + 1_000L)
        device.connect()
        switcher.switchTo(cast)
        settle()
        cast.playWhenReady shouldBe false

        cast.play()
        settle()
        device.drop()
        switcher.switchTo(local)
        settleHere()
        local.play()
        local.pause()
        settleHere()
        device.connect()
        switcher.switchTo(cast)
        settle()
        cast.playWhenReady shouldBe false
    }

    @Test
    fun `a long queue goes over whole, however quickly the cast ends`() {
        val songs = (0 until 50).map { "s$it" }
        local.setMediaItems(items(*songs.toTypedArray()), 30, 5_000L)
        local.prepare()
        settleHere()

        switcher.switchTo(cast)
        switcher.switchTo(local)
        settleHere()

        local.ids shouldBe songs
        local.currentMediaItemIndex shouldBe 30
    }

    @Test
    fun `with nothing here to cast, what the cast device is playing is taken up`() {
        // Another of this app's players sent it, before the service started again
        val before = CastQueuePlayer(device, sources)
        before.setMediaItems(items("a", "b", "c"))
        before.playWhenReady = true
        before.prepare()
        settle()
        before.release()
        val again = CastQueuePlayer(device, sources)

        switcher.switchTo(again)

        again.ids shouldBe listOf("a", "b", "c")
        again.isPlaying shouldBe true
        again.release()
    }

    @Test
    fun `a volume asked for in an earlier cast isn't this device's after a later cast that asked for none`() {
        local.load(index = 0, positionMs = 0L, play = false)
        switcher.switchTo(cast)
        settle()
        cast.volume = 0.2f
        device.drop()
        switcher.switchTo(local)
        settleHere()
        local.volume = 1f

        device.connect()
        switcher.switchTo(cast)
        settle()
        cast.volume shouldBe 1f
        device.drop()
        switcher.switchTo(local)
        settleHere()

        local.volume shouldBe 1f
    }

    @Test
    fun `the session is on the new player before the one left behind stops, so it never reads stopped mid-switch`() {
        local.load(index = 0, positionMs = 0L, play = true)
        var stateWhenSwitched: Int? = null
        val watching = SessionPlayerSwitcher(local, setSessionPlayer = { stateWhenSwitched = local.playbackState })

        watching.switchTo(cast)

        stateWhenSwitched shouldNotBe Player.STATE_IDLE
        local.playbackState shouldBe Player.STATE_IDLE
    }

    @Test
    fun `casting again with nothing here doesn't bring back the queue of the cast before`() {
        local.load(index = 0, positionMs = 0L, play = true)
        switcher.switchTo(cast)
        settle()
        cast.stop()
        settle()
        device.drop()
        switcher.switchTo(local)
        settleHere()
        // As an app stops playback here
        local.clearMediaItems()
        settleHere()

        device.connect()
        switcher.switchTo(cast)
        settle()

        cast.mediaItemCount shouldBe 0
    }

    private class Wrapped(player: Player) : ForwardingPlayer(player)

    /** A wrapper whose places are [startMs] on from its player's. */
    private class Offset(player: Player, private val startMs: Long) : ForwardingPlayer(player) {
        override fun getCurrentPosition() = super.getCurrentPosition() + startMs

        override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) =
            super.setMediaItems(mediaItems, startIndex, startPositionMs - startMs)
    }

    private companion object {
        const val SONG_MS = 100_000L
        const val SETTLE_MS = 1_000L
        const val SLACK_MS = 1_000L
    }
}
