package org.jellyfin.mobile.cast

import android.app.Application
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.longs.shouldBeInRange
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.cast.CastDevice.Repeat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * The queue owned here, with a window of it on the cast device. The cast device is a
 * [FakeCastDevice], which takes each command a round trip later and plays as time goes on; songs
 * last [SONG_MS], and those in [transcodes] are cast as a stream that starts where it's asked for.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
@Config(application = Application::class)
class CastQueuePlayerTest {

    private val device = FakeCastDevice()
    private var transcodes = setOf<String>()

    /** The streams asked for: the song and from where. */
    private val asked = mutableListOf<Pair<String, Long>>()

    private fun stream(item: MediaItem, fromMs: Long): CastStream {
        asked += item.mediaId to fromMs
        return if (item.mediaId in transcodes) {
            CastStream(
                "https://vault.test/${item.mediaId}?from=$fromMs",
                "audio/mpeg",
                startMs = fromMs,
                seekable = false,
            )
        } else {
            CastStream("https://vault.test/${item.mediaId}", "audio/flac")
        }
    }

    private val player = CastQueuePlayer(device, ::stream)

    init {
        device.connect()
    }

    @After
    fun tearDown() {
        player.release()
    }

    private fun item(id: String, durationMs: Long = SONG_MS) = MediaItem.Builder()
        .setMediaId(id)
        .setUri("https://vault.test/local/$id")
        .setMediaMetadata(MediaMetadata.Builder().setTitle("Song $id").setDurationMs(durationMs).build())
        .build()

    private fun items(vararg ids: String) = ids.map { item(it) }

    private fun settle(ms: Long = SETTLE_MS) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun playing(vararg ids: String, index: Int = 0, positionMs: Long = 0L, play: Boolean = true) {
        player.setMediaItems(items(*ids), index, positionMs)
        player.playWhenReady = play
        player.prepare()
        settle()
    }

    private val Player.ids get() = (0 until mediaItemCount).map { getMediaItemAt(it).mediaId }

    /** The songs on the cast device, by their ids here. */
    private val onDevice get() = device.queue.map { it.item.mediaId }

    private fun Long.near(expectedMs: Long) = shouldBeInRange(expectedMs - SLACK_MS..expectedMs + SLACK_MS)

    @Test
    fun `the cast device gets the song playing and the two after it, not the whole queue`() {
        playing("a", "b", "c", "d", "e", index = 1)

        onDevice shouldBe listOf("b", "c", "d")
        device.playing?.item?.mediaId shouldBe "b"
        player.ids shouldBe listOf("a", "b", "c", "d", "e")
        player.currentMediaItemIndex shouldBe 1
        player.playbackState shouldBe Player.STATE_READY
        player.isPlaying shouldBe true
    }

    @Test
    fun `the queue and the song playing read as the app's items, metadata and all`() {
        playing("a", "b")

        player.currentMediaItem?.mediaId shouldBe "a"
        player.mediaMetadata.title shouldBe "Song a"
        player.getMediaItemAt(1).mediaMetadata.title shouldBe "Song b"
        player.currentTimeline.windowCount shouldBe 2
    }

    @Test
    fun `as the cast device moves on, this follows it and tops the window up`() {
        playing("a", "b", "c", "d", "e")

        settle(SONG_MS)

        player.currentMediaItemIndex shouldBe 1
        onDevice shouldBe listOf("b", "c", "d")
        player.currentPosition.near(SETTLE_MS)
    }

    @Test
    fun `an app's burst of edits around the song playing lands the right songs, without starting it again`() {
        playing("a", "b", "c", "d", "e", index = 2)
        settle(10_000L)
        val loads = device.commands.count { it.startsWith("load") }

        // How TuneVault swaps a new queue in around the song playing
        player.removeMediaItems(3, 5)
        player.removeMediaItems(0, 2)
        player.addMediaItems(0, items("x", "y"))
        player.addMediaItems(items("z", "w"))
        settle()

        player.ids shouldBe listOf("x", "y", "c", "z", "w")
        player.currentMediaItemIndex shouldBe 2
        onDevice shouldBe listOf("c", "z", "w")
        device.commands.count { it.startsWith("load") } shouldBe loads
        device.songPositionMs.near(10_000L + SETTLE_MS)
    }

    @Test
    fun `moving songs around the one playing lands them in order on the cast device`() {
        playing("a", "b", "c", "d", "e")

        player.moveMediaItem(4, 1)
        player.moveMediaItem(3, 2)
        settle()

        player.ids shouldBe listOf("a", "e", "c", "b", "d")
        onDevice shouldBe listOf("a", "e", "c")
    }

    @Test
    fun `a new queue the same length as the old one replaces it`() {
        playing("a", "b", "c")

        player.setMediaItems(items("x", "y", "z"))
        player.currentMediaItem?.mediaId shouldBe "x"
        settle()

        onDevice shouldBe listOf("x", "y", "z")
        device.playing?.item?.mediaId shouldBe "x"
    }

    @Test
    fun `a seek while the queue is on its way lands where it was asked for`() {
        player.setMediaItems(items("a", "b"))
        player.playWhenReady = true
        player.prepare()
        player.seekTo(30_000L)

        player.currentPosition shouldBe 30_000L
        settle()

        device.playing?.item?.mediaId shouldBe "a"
        device.songPositionMs.near(30_000L + SETTLE_MS)
    }

    @Test
    fun `quick skips land on the song skipped to`() {
        playing("a", "b", "c", "d", "e")

        player.seekToNextMediaItem()
        player.seekToNextMediaItem()
        player.currentMediaItemIndex shouldBe 2
        settle()

        player.currentMediaItemIndex shouldBe 2
        device.playing?.item?.mediaId shouldBe "c"
    }

    @Test
    fun `while a skip is on its way, the place and a seek are the song skipped to's`() {
        playing("a", "b", "c", "d", "e")
        settle(50_000L)

        player.seekTo(3, 0L)
        player.seekTo(20_000L)
        player.currentPosition shouldBe 20_000L
        settle()

        device.playing?.item?.mediaId shouldBe "d"
        device.songPositionMs.near(20_000L + SETTLE_MS)
    }

    @Test
    fun `a song served as it is seeks on the cast device`() {
        playing("a", "b")
        val loads = device.commands.count { it.startsWith("load") }

        player.seekTo(30_000L)
        settle()

        device.commands shouldContain "seek 30000"
        device.commands.count { it.startsWith("load") } shouldBe loads
        player.currentPosition.near(30_000L + SETTLE_MS)
    }

    @Test
    fun `a transcode is cast from where it's asked for, and places are the song's`() {
        transcodes = setOf("a")
        playing("a", "b", positionMs = 60_000L)

        asked shouldContain ("a" to 60_000L)
        device.streamPositionMs.near(SETTLE_MS)
        player.currentPosition.near(60_000L + SETTLE_MS)
        player.duration shouldBe SONG_MS

        player.seekTo(20_000L)
        settle()

        asked shouldContain ("a" to 20_000L)
        device.playing?.stream?.startMs shouldBe 20_000L
        player.currentPosition.near(20_000L + SETTLE_MS)
    }

    @Test
    fun `repeating a song keeps it on the cast device`() {
        playing("a", "b", "c")

        player.repeatMode = Player.REPEAT_MODE_ONE
        settle()
        device.repeat shouldBe Repeat.ONE
        onDevice shouldBe listOf("a")

        settle(SONG_MS)
        player.currentMediaItemIndex shouldBe 0
        device.playing?.item?.mediaId shouldBe "a"
    }

    @Test
    fun `repeating a queue short enough to send whole repeats it there`() {
        playing("a", "b", index = 1)

        player.repeatMode = Player.REPEAT_MODE_ALL
        settle()

        onDevice shouldBe listOf("b", "a")
        device.repeat shouldBe Repeat.ALL
    }

    @Test
    fun `repeating a longer queue goes round to its start as the window moves`() {
        playing("a", "b", "c", "d", "e", index = 4)

        player.repeatMode = Player.REPEAT_MODE_ALL
        settle()

        onDevice shouldBe listOf("e", "a", "b")
        device.repeat shouldBe Repeat.OFF
        settle(SONG_MS)
        player.currentMediaItemIndex shouldBe 0
    }

    @Test
    fun `stopped, the queue stays here, and playing again picks up where it was`() {
        playing("a", "b")
        settle(10_000L)

        player.stop()
        settle()
        player.playbackState shouldBe Player.STATE_IDLE
        player.ids shouldBe listOf("a", "b")
        device.queue.shouldBeEmpty()

        player.prepare()
        settle()
        device.playing?.item?.mediaId shouldBe "a"
        device.songPositionMs.near(10_000L + 2 * SETTLE_MS)
        device.isPlaying shouldBe true
    }

    @Test
    fun `another app casting to the cast device leaves the queue here, stopped, and sends it nothing`() {
        playing("a", "b")

        device.takeOver()
        val commands = device.commands.size
        settle()

        player.playbackState shouldBe Player.STATE_IDLE
        player.playWhenReady shouldBe false
        player.ids shouldBe listOf("a", "b")
        device.commands.size shouldBe commands
    }

    @Test
    fun `a command the cast device fails sends the queue again from where it is`() {
        playing("a", "b")

        device.failNext = 1
        player.seekTo(40_000L)
        settle()

        device.commands.last() shouldBe "load [${device.keys[0]}, ${device.keys[1]}] @40000"
        device.songPositionMs.near(40_000L + SETTLE_MS)
        player.playerError.shouldBeNull()
    }

    @Test
    fun `failing again is an error, rather than silence`() {
        player.setMediaItems(items("a"))
        player.prepare()
        device.failNext = 2
        settle()

        player.playerError.shouldNotBeNull()
        player.playbackState shouldBe Player.STATE_IDLE
    }

    @Test
    fun `a cast device that can't play the song is an error`() {
        playing("a")

        device.failToPlay()

        player.playerError.shouldNotBeNull()
        player.playbackState shouldBe Player.STATE_IDLE
    }

    @Test
    fun `paused, played or skipped on the TV's remote, this follows`() {
        playing("a", "b", "c")

        device.pressPause()
        player.playWhenReady shouldBe false
        settle()
        device.isPlaying shouldBe false

        device.pressPlay()
        player.playWhenReady shouldBe true

        device.skipOnRemote()
        player.currentMediaItemIndex shouldBe 1
    }

    @Test
    fun `stopped on the cast device, the queue stays here, stopped`() {
        playing("a", "b")

        device.stopOnRemote()
        settle()

        player.playbackState shouldBe Player.STATE_IDLE
        player.ids shouldBe listOf("a", "b")
    }

    @Test
    fun `after the last song, playback has ended`() {
        playing("a", "b", index = 1)

        settle(SONG_MS)

        player.playbackState shouldBe Player.STATE_ENDED
        player.currentMediaItemIndex shouldBe 1
    }

    @Test
    fun `removing the song playing moves on to the next, from its start`() {
        playing("a", "b", "c")
        settle(10_000L)

        player.removeMediaItem(0)
        settle()

        player.currentMediaItem?.mediaId shouldBe "b"
        device.playing?.item?.mediaId shouldBe "b"
        device.songPositionMs.near(SETTLE_MS)
    }

    @Test
    fun `changes past the window send the cast device nothing`() {
        playing("a", "b", "c", "d", "e")
        val commands = device.commands.size

        player.addMediaItems(items("f", "g"))
        player.removeMediaItem(4)
        settle()

        device.commands.size shouldBe commands
    }

    @Test
    fun `speed goes to the cast device`() {
        playing("a")

        player.playbackParameters = PlaybackParameters(1.5f)
        settle()

        device.speed shouldBe 1.5f
    }

    @Test
    fun `a volume asked for is kept for later, and the cast device's stays as it was`() {
        playing("a")

        player.volume = 0.3f

        player.volumeAskedFor shouldBe 0.3f
        device.volume shouldBe 10
    }

    @Test
    fun `the device volume is the cast device's`() {
        playing("a")

        player.setDeviceVolume(15, 0)

        device.volume shouldBe 15
        player.deviceVolume shouldBe 15
        player.deviceInfo.playbackType shouldBe androidx.media3.common.DeviceInfo.PLAYBACK_TYPE_REMOTE
    }

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
    fun `a cast device that went quiet before the cast ended is left as far on as it would have got`() {
        playing("a", "b", "c")
        settle(90_000L)

        device.quiet = true
        settle(30_000L)
        device.drop()

        player.currentMediaItemIndex shouldBe 1
        player.currentPosition.near(20_000L + SETTLE_MS)
    }

    @Test
    fun `another player of this app's can take up what's on the cast device`() {
        playing("a", "b", "c", "d", index = 1)
        player.release()
        val next = CastQueuePlayer(device, ::stream)

        next.takeUpDevice()

        next.ids shouldBe listOf("b", "c", "d")
        next.currentMediaItemIndex shouldBe 0
        next.isPlaying shouldBe true
        next.release()
    }

    @Test
    fun `answers that come before the cast device's report land the same`() {
        val answersFirst = FakeCastDevice(answerFirst = true)
        val other = CastQueuePlayer(answersFirst, ::stream)
        answersFirst.connect()
        other.setMediaItems(items("a", "b", "c", "d", "e"))
        other.playWhenReady = true
        other.prepare()
        settle()

        other.seekToNextMediaItem()
        other.seekToNextMediaItem()
        other.seekTo(20_000L)
        other.removeMediaItem(4)
        other.addMediaItems(3, items("x"))
        settle()

        other.ids shouldBe listOf("a", "b", "c", "x", "d")
        other.currentMediaItemIndex shouldBe 2
        answersFirst.queue.map { it.item.mediaId } shouldBe listOf("c", "x", "d")
        answersFirst.songPositionMs.near(20_000L + SETTLE_MS)
        answersFirst.commands.count { it.startsWith("load") } shouldBe 1
        other.release()
    }

    @Test
    fun `released, nothing more is sent`() {
        playing("a")

        player.release()

        device.released shouldBe true
    }

    @Test
    fun `each entry is its own, so a song queued twice is two songs on the cast device`() {
        playing("a", "a", "b")

        device.keys.toSet().size shouldBe 3
        settle(SONG_MS)
        player.currentMediaItemIndex shouldBe 1
        device.commands.filter { it.startsWith("load") }.size shouldBe 1
    }

    @Test
    fun `emptied, the cast device stops`() {
        playing("a", "b")

        player.clearMediaItems()
        settle()

        device.queue.shouldBeEmpty()
        player.playbackState shouldBe Player.STATE_ENDED
    }

    private companion object {
        const val SONG_MS = 100_000L

        /** Time enough for the commands a change takes, each a round trip. */
        const val SETTLE_MS = 1_000L
        const val SLACK_MS = 1_000L
    }
}
