package org.jellyfin.mobile.cast

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.cast.CastDevice.Repeat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Playback and the queue on the cast device (see [CastQueuePlayerFixture]). */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
@Config(application = Application::class)
internal class CastQueuePlayerTest : CastQueuePlayerFixture() {

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
    fun `a paused cast device that buffers (a seek on its remote) stays paused here, and isn't told to play`() {
        playing("a")
        device.pressPause()
        settle()

        device.seekOnRemote(50_000L)
        player.playWhenReady shouldBe false
        settle()

        player.playWhenReady shouldBe false
        device.isPlaying shouldBe false
        device.commands.last() shouldBe "pause"
        player.currentPosition.near(50_000L)
    }

    @Test
    fun `a playing cast device that buffers plays on, here too`() {
        playing("a")

        device.seekOnRemote(50_000L)
        player.playWhenReady shouldBe true
        settle()

        player.isPlaying shouldBe true
        device.isPlaying shouldBe true
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
    fun `skips back and forward as far as this device's player does`() {
        val skips = CastQueuePlayer(device, sources, seekBackMs = 10_000L, seekForwardMs = 30_000L)
        skips.setMediaItems(items("a"), 0, 50_000L)
        skips.prepare()
        settle()

        skips.seekBackIncrement shouldBe 10_000L
        skips.seekForwardIncrement shouldBe 30_000L
        skips.seekBack()
        skips.currentPosition shouldBe 40_000L
        skips.seekForward()
        skips.currentPosition shouldBe 70_000L
        skips.release()
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
    fun `a pause on the TV's remote while the window is topped up stays paused`() {
        playing("a", "b", "c", "d")
        device.onCommand = {
            if (it.startsWith("keepOnly")) Handler(Looper.getMainLooper()).post { device.pressPause() }
        }

        settle(SONG_MS)

        device.commands.filter { it.startsWith("keepOnly") }.shouldNotBeEmpty()
        player.playWhenReady shouldBe false
        device.isPlaying shouldBe false
    }

    @Test
    fun `pressing play after the cast device couldn't play a song tries it again`() {
        playing("a")
        device.failToPlay()
        player.playerError.shouldNotBeNull()

        player.prepare()
        settle()

        player.playerError.shouldBeNull()
        device.isPlaying shouldBe true
        player.playbackState shouldBe Player.STATE_READY
    }

    @Test
    fun `nothing goes to the cast device until a song's source is found, and each is looked for once`() {
        val later = FakeCastDevice()
        val looked = mutableListOf<String>()
        val found = mutableMapOf<String, (CastSource) -> Unit>()
        val slow = CastQueuePlayer(
            later,
            CastSourceFinder { item, give ->
                looked += item.mediaId
                found[item.mediaId] = give
            },
        )
        later.connect()
        slow.setMediaItems(items("a", "b", "c", "d"))
        slow.playWhenReady = true
        slow.prepare()
        settle()

        later.commands.shouldBeEmpty()
        slow.playbackState shouldBe Player.STATE_BUFFERING
        looked shouldBe listOf("a", "b", "c")

        // The next songs aren't waited for: the one playing goes as soon as it can
        found.getValue("a")(CastSource { stream(item("a"), it) })
        settle()
        later.queue.map { it.item.mediaId } shouldBe listOf("a")
        slow.isPlaying shouldBe true

        found.getValue("c")(CastSource { stream(item("c"), it) })
        found.getValue("b")(CastSource { stream(item("b"), it) })
        settle()
        later.queue.map { it.item.mediaId } shouldBe listOf("a", "b", "c")

        slow.seekTo(10_000L)
        slow.seekToNextMediaItem()
        settle()
        looked shouldBe listOf("a", "b", "c", "d")
        slow.release()
    }

    @Test
    fun `answers that come before the cast device's report land the same`() {
        val answersFirst = FakeCastDevice(answerFirst = true)
        val other = CastQueuePlayer(answersFirst, sources)
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
}
