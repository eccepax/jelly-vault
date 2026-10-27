package org.jellyfin.mobile.cast

import android.app.Application
import android.os.Bundle
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** The cast queue kept as it was sent: every change to the player's playlist is made to it too. */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
@Config(application = Application::class)
class RemotePlaylistTest {

    private val player: ExoPlayer = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
        .setMediaSourceFactory(FakeAudioSourceFactory())
        .build()
    private val playlist = RemotePlaylist(player)

    @After
    fun tearDown() {
        player.release()
    }

    private fun item(id: String) = MediaItem.Builder().setMediaId(id).setUri("https://vault.test/$id").build()

    /** Until the player has done what it was told, and any queue waiting to go again has gone. */
    private fun settle() {
        advance(player).untilPendingCommandsAreFullyHandled()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(RESEND_AFTER_MS))
        advance(player).untilPendingCommandsAreFullyHandled()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private val Player.ids get() = (0 until mediaItemCount).map { getMediaItemAt(it).mediaId }

    private fun matchesThePlayer() {
        settle()
        playlist.items.map { it.mediaId } shouldBe player.ids
    }

    @Test
    fun `setting the queue in any of its forms`() {
        playlist.setMediaItems(listOf(item("a"), item("b")))
        matchesThePlayer()
        playlist.setMediaItems(listOf(item("c")), true)
        matchesThePlayer()
        playlist.setMediaItems(listOf(item("d"), item("e")), 1, 0L)
        matchesThePlayer()
        playlist.setMediaItem(item("f"))
        matchesThePlayer()
        playlist.setMediaItem(item("g"), 0L)
        matchesThePlayer()
        playlist.setMediaItem(item("h"), false)
        matchesThePlayer()
    }

    @Test
    fun `adding, at the end or in place`() {
        playlist.setMediaItems(listOf(item("a"), item("b")))
        playlist.addMediaItem(item("c"))
        playlist.addMediaItem(0, item("d"))
        playlist.addMediaItems(listOf(item("e"), item("f")))
        playlist.addMediaItems(2, listOf(item("g")))

        matchesThePlayer()
        playlist.items.map { it.mediaId } shouldBe listOf("d", "a", "g", "b", "c", "e", "f")
    }

    @Test
    fun `moving, replacing and removing`() {
        playlist.setMediaItems(listOf("a", "b", "c", "d", "e").map(::item))
        playlist.moveMediaItem(0, 4)
        matchesThePlayer()
        playlist.moveMediaItems(0, 2, 1)
        matchesThePlayer()
        playlist.replaceMediaItem(1, item("x"))
        matchesThePlayer()
        playlist.replaceMediaItems(2, 4, listOf(item("y")))
        matchesThePlayer()
        playlist.removeMediaItem(0)
        matchesThePlayer()
        playlist.removeMediaItems(0, 1)
        matchesThePlayer()
        playlist.clearMediaItems()
        matchesThePlayer()
    }

    @Test
    fun `out-of-range changes are clamped the way the player clamps them`() {
        playlist.setMediaItems(listOf("a", "b", "c").map(::item))
        playlist.addMediaItems(10, listOf(item("d")))
        playlist.removeMediaItems(2, 10)
        playlist.moveMediaItems(0, 1, 10)

        matchesThePlayer()
    }

    @Test
    fun `asked what's playing, it answers with the song as sent, not the cast player's placeholder`() {
        val placeholders = RemotePlaylist(Placeholders(player))
        val chapters = Bundle().apply { putLongArray("chapterStarts", longArrayOf(0L, 30_000L)) }
        val withChapters = item("b").buildUpon()
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Chapter book").setExtras(chapters).build())
            .build()
        placeholders.setMediaItems(listOf(item("a"), withChapters), 1, 0L)
        settle()

        placeholders.currentMediaItem?.mediaId shouldBe "b"
        placeholders.currentMediaItem?.localConfiguration?.uri.toString() shouldBe "https://vault.test/b"
        placeholders.getMediaItemAt(0).mediaId shouldBe "a"
        placeholders.mediaMetadata.title.toString() shouldBe "Chapter book"
        placeholders.mediaMetadata.extras?.getLongArray("chapterStarts")?.toList() shouldBe listOf(0L, 30_000L)
    }

    @Test
    fun `when its copy and the player disagree, the player's own answer stands`() {
        val placeholders = RemotePlaylist(Placeholders(player))
        placeholders.setMediaItems(listOf(item("a")))
        settle()
        // Changed behind its back, e.g. the cast session ending
        player.clearMediaItems()
        settle()

        placeholders.currentMediaItem shouldBe null
    }

    @Test
    fun `the queue it reports, and every event about it, carries the songs as sent`() {
        val castLike: ExoPlayer = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
            .setMediaSourceFactory(FakeAudioSourceFactory(placeholders = true))
            .build()
        try {
            val playlist = RemotePlaylist(castLike)
            val timelines = mutableListOf<List<String>>()
            val transitions = mutableListOf<String?>()
            val titles = mutableListOf<String?>()
            playlist.addListener(
                object : Player.Listener {
                    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                        timelines += List(timeline.windowCount) { timeline.getWindow(it, Timeline.Window()).mediaItem.mediaId }
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        transitions += mediaItem?.mediaId
                    }

                    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                        titles += mediaMetadata.title?.toString()
                    }
                },
            )
            val titled = listOf("a", "b").map {
                item(it).buildUpon().setMediaMetadata(MediaMetadata.Builder().setTitle("Title $it").build()).build()
            }

            playlist.setMediaItems(titled)
            playlist.prepare()
            advance(castLike).untilPendingCommandsAreFullyHandled()
            shadowOf(Looper.getMainLooper()).idle()
            playlist.seekTo(1, 0L)
            advance(castLike).untilPendingCommandsAreFullyHandled()
            shadowOf(Looper.getMainLooper()).idle()

            val reported = playlist.currentTimeline
            List(reported.windowCount) { reported.getWindow(it, Timeline.Window()).mediaItem.mediaId } shouldBe listOf("a", "b")
            timelines.last() shouldBe listOf("a", "b")
            transitions.last() shouldBe "b"
            titles.last() shouldBe "Title b"
        } finally {
            castLike.release()
        }
    }

    @Test
    fun `a listener taken off hears nothing more`() {
        val heard = mutableListOf<Int>()
        val listener = object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                heard += timeline.windowCount
            }
        }
        playlist.addListener(listener)
        playlist.removeListener(listener)

        playlist.setMediaItems(listOf(item("a")))
        settle()

        heard shouldBe emptyList()
    }

    @Test
    fun `the song playing reads as sent while the queue around it changes, as an app's queue edits do`() {
        val placeholders = RemotePlaylist(Placeholders(player))
        placeholders.setMediaItems(listOf("a", "b", "c").map(::item), 1, 0L)
        settle()

        // How TuneVault swaps out everything around the playing song (Play next, shuffle, ...)
        placeholders.removeMediaItems(2, 3)
        placeholders.removeMediaItems(0, 1)
        placeholders.addMediaItems(0, listOf(item("x")))
        placeholders.addMediaItems(listOf(item("y"), item("z")))
        settle()

        placeholders.currentMediaItem?.mediaId shouldBe "b"
        placeholders.ids shouldBe listOf("x", "b", "y", "z")
    }

    @Test
    fun `a long queue goes to the cast device a message at a time, the song to play first`() {
        val messages = Messages(player)
        val playlist = RemotePlaylist(messages)
        val songs = List(50) { item("s$it") }

        playlist.setMediaItems(songs, 25, 10_000L)

        // The first message: the song to play and those after it
        messages.sizes.first() shouldBe SONGS_PER_MESSAGE
        settleAll()
        player.ids shouldBe songs.map { it.mediaId }
        player.currentMediaItem?.mediaId shouldBe "s25"
        player.currentPosition shouldBe 10_000L
        messages.sizes.all { it <= SONGS_PER_MESSAGE } shouldBe true
        playlist.items.map { it.mediaId } shouldBe songs.map { it.mediaId }
        playlist.currentMediaItem?.mediaId shouldBe "s25"
    }

    @Test
    fun `a change while a long queue is still being sent reaches the cast device too`() {
        val songs = List(50) { item("s$it") }
        playlist.setMediaItems(songs, 0, 0L)

        playlist.addMediaItems(listOf(item("x")))
        settleAll()

        player.ids shouldBe songs.map { it.mediaId } + "x"
        player.currentMediaItem?.mediaId shouldBe "s0"
        playlist.items.map { it.mediaId } shouldBe player.ids
    }

    @Test
    fun `a long queue set without a place starts at its first song`() {
        val songs = List(30) { item("s$it") }

        playlist.setMediaItems(songs)
        settleAll()

        player.ids shouldBe songs.map { it.mediaId }
        player.currentMediaItemIndex shouldBe 0
    }

    @Test
    fun `while a long queue is on its way, it answers for all of it`() {
        val songs = List(50) { item("s$it") }

        playlist.setMediaItems(songs, 30, 0L)
        settle()

        playlist.mediaItemCount shouldBe 50
        playlist.currentMediaItemIndex shouldBe 30
        playlist.currentMediaItem?.mediaId shouldBe "s30"
        playlist.getMediaItemAt(45).mediaId shouldBe "s45"
        val timeline = playlist.currentTimeline
        List(timeline.windowCount) { timeline.getWindow(it, Timeline.Window()).mediaItem.mediaId } shouldBe
            songs.map { it.mediaId }
        playlist.nextMediaItemIndex shouldBe 31
        playlist.previousMediaItemIndex shouldBe 29
    }

    @Test
    fun `a seek to a song not on the cast device yet sends the queue again from there`() {
        val songs = List(50) { item("s$it") }
        playlist.setMediaItems(songs, 30, 0L)

        playlist.seekTo(5, 0L)
        settleAll()

        player.ids shouldBe songs.map { it.mediaId }
        player.currentMediaItem?.mediaId shouldBe "s5"
        playlist.currentMediaItemIndex shouldBe 5
    }

    @Test
    fun `skips made before the cast device has the queue don't send it again`() {
        val device = SlowDevice(player)
        val playlist = RemotePlaylist(device)
        val songs = List(50) { item("s$it") }
        playlist.setMediaItems(songs, 0, 0L)

        repeat(3) { playlist.seekToNextMediaItem() }
        playlist.currentMediaItemIndex shouldBe 3
        device.deliverAll()

        device.loads shouldBe 1
        player.ids shouldBe songs.map { it.mediaId }
        player.currentMediaItem?.mediaId shouldBe "s3"
        playlist.currentMediaItem?.mediaId shouldBe "s3"
    }

    @Test
    fun `a skip the cast device hasn't made yet stays on its song while songs arrive in front of it`() {
        val device = SlowDevice(player)
        val playlist = RemotePlaylist(device)
        val songs = List(50) { item("s$it") }
        playlist.setMediaItems(songs, 45, 0L)
        device.deliver()

        playlist.seekToNextMediaItem()
        playlist.currentMediaItem?.mediaId shouldBe "s46"
        // The songs before it arrive: the cast player's answer, the index asked for, is now another song
        device.deliver()

        playlist.currentMediaItem?.mediaId shouldBe "s46"
        device.deliverAll()
        player.ids shouldBe songs.map { it.mediaId }
        player.currentMediaItem?.mediaId shouldBe "s46"
        playlist.currentMediaItem?.mediaId shouldBe "s46"
    }

    @Test
    fun `while a long queue is on its way, its songs' ids can't be mistaken for the cast device's`() {
        val device = SlowDevice(player)
        val playlist = RemotePlaylist(device)
        playlist.setMediaItems(List(50) { item("s$it") }, 30, 0L)

        (playlist.currentTimeline.getUidOfPeriod(1) is Int) shouldBe false
    }

    /**
     * Like a cast device behind Media3's cast player: each change reaches it only once [deliver]ed,
     * and until then the player answers as before, except that a skip answers with the index asked
     * for. Delivered, a skip goes to the song that was at that index, as the cast player jumps to an
     * item id.
     */
    private class SlowDevice(private val device: ExoPlayer) : ForwardingPlayer(device) {
        private val undelivered = ArrayDeque<() -> Unit>()
        private var seekingTo: Int? = null

        /** Times the whole queue was sent, rather than added to. */
        var loads = 0

        override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
            loads++
            undelivered += { super.setMediaItems(mediaItems, startIndex, startPositionMs) }
        }

        override fun addMediaItems(mediaItems: List<MediaItem>) {
            undelivered += { super.addMediaItems(mediaItems) }
        }

        override fun addMediaItems(index: Int, mediaItems: List<MediaItem>) {
            undelivered += { super.addMediaItems(index, mediaItems) }
        }

        override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
            val song = getMediaItemAt(mediaItemIndex).mediaId
            seekingTo = mediaItemIndex
            undelivered += {
                seekingTo = null
                super.seekTo((0 until mediaItemCount).first { getMediaItemAt(it).mediaId == song }, positionMs)
            }
        }

        override fun getCurrentMediaItemIndex(): Int = seekingTo ?: super.getCurrentMediaItemIndex()

        override fun getCurrentMediaItem(): MediaItem? =
            if (mediaItemCount == 0) null else getMediaItemAt(currentMediaItemIndex)

        /** The cast device gets the next change, and reports what it then has. */
        fun deliver() {
            undelivered.removeFirstOrNull()?.invoke()
            advance(device).untilPendingCommandsAreFullyHandled()
            shadowOf(Looper.getMainLooper()).idle()
        }

        fun deliverAll() {
            while (undelivered.isNotEmpty()) deliver()
        }
    }

    @Test
    fun `a song not on the cast device yet is the one removed`() {
        val songs = List(50) { item("s$it") }
        playlist.setMediaItems(songs, 30, 0L)

        playlist.removeMediaItem(2)
        settleAll()

        player.ids shouldBe songs.map { it.mediaId } - "s2"
        player.currentMediaItem?.mediaId shouldBe "s30"
    }

    @Test
    fun `swapping out everything around the song playing while a long queue is on its way goes once, without repeats`() {
        val messages = Messages(player)
        val playlist = RemotePlaylist(messages)
        val songs = List(60) { item("s$it") }
        playlist.setMediaItems(songs, 0, 0L)
        val shuffled = songs.drop(1).reversed()

        // How TuneVault swaps out everything around the playing song (shuffle, Play next, ...)
        val playing = playlist.currentMediaItemIndex
        playlist.removeMediaItems(playing + 1, playlist.mediaItemCount)
        playlist.removeMediaItems(0, playing)
        playlist.addMediaItems(0, emptyList())
        playlist.addMediaItems(shuffled)
        settleAll()

        player.ids shouldBe listOf("s0") + shuffled.map { it.mediaId }
        player.currentMediaItem?.mediaId shouldBe "s0"
        messages.loads shouldBe 2
    }

    @Test
    fun `adding more songs than a message holds sends the queue again a part at a time`() {
        val messages = Messages(player)
        val playlist = RemotePlaylist(messages)
        playlist.setMediaItems(listOf(item("a")))
        settle()
        val more = List(45) { item("s$it") }

        playlist.addMediaItems(more)
        settleAll()

        player.ids shouldBe listOf("a") + more.map { it.mediaId }
        messages.sizes.all { it <= SONGS_PER_MESSAGE } shouldBe true
    }

    @Test
    fun `a cast device that keeps dropping songs gets the queue again, then its own answers stand`() {
        val dropping = DropsTheLastAdded(player)
        val playlist = RemotePlaylist(dropping)
        val songs = List(50) { item("s$it") }

        playlist.setMediaItems(songs, 0, 0L)
        settleAll()

        dropping.loads shouldBe 1 + MAX_RESENDS
        playlist.mediaItemCount shouldBe player.mediaItemCount
        playlist.currentMediaItem?.mediaId shouldBe player.currentMediaItem?.mediaId
    }

    /** Like a receiver that loses a song from each part added to its queue. */
    private class DropsTheLastAdded(player: Player) : ForwardingPlayer(player) {
        var loads = 0

        override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
            loads++
            super.setMediaItems(mediaItems, startIndex, startPositionMs)
        }

        override fun addMediaItems(mediaItems: List<MediaItem>) = super.addMediaItems(mediaItems.dropLast(1))

        override fun addMediaItems(index: Int, mediaItems: List<MediaItem>) =
            super.addMediaItems(index, mediaItems.dropLast(1))
    }

    /** Until the cast device has every part of a long queue: each part goes once the last arrives. */
    private fun settleAll() = repeat(SONGS_PER_MESSAGE) { settle() }

    /** How many songs each message to the cast device carried. */
    private class Messages(player: Player) : ForwardingPlayer(player) {
        val sizes = mutableListOf<Int>()

        /** Times the whole queue was sent, rather than added to. */
        var loads = 0

        override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
            loads++
            sizes += mediaItems.size
            super.setMediaItems(mediaItems, startIndex, startPositionMs)
        }

        override fun addMediaItems(mediaItems: List<MediaItem>) {
            sizes += mediaItems.size
            super.addMediaItems(mediaItems)
        }

        override fun addMediaItems(index: Int, mediaItems: List<MediaItem>) {
            sizes += mediaItems.size
            super.addMediaItems(index, mediaItems)
        }
    }

    @Test
    fun `the items kept are the ones sent, with their uris`() {
        playlist.setMediaItems(listOf(item("a")))

        playlist.items.single().localConfiguration?.uri.toString() shouldBe "https://vault.test/a"
    }
}
