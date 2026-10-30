package org.jellyfin.mobile.cast

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A cast device describes only the songs around the one playing: the rest of its queue comes back
 * as the app sent it, looked up by the cast device's own id for each.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
@Config(application = Application::class)
class CastQueueDetailsTest {

    private val exo = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
        .setMediaSourceFactory(FakeAudioSourceFactory())
        .build()

    /** What the cast device's player has: its own id for each song, and the songs it described. */
    private var castQueue: List<Pair<Int, MediaItem>> = emptyList()
    private var remote = true

    private val castDevice = object : ForwardingPlayer(exo) {
        override fun getDeviceInfo(): DeviceInfo =
            DeviceInfo.Builder(if (remote) DeviceInfo.PLAYBACK_TYPE_REMOTE else DeviceInfo.PLAYBACK_TYPE_LOCAL).build()

        override fun getCurrentTimeline(): Timeline =
            if (remote) CastQueueTimeline(castQueue) else super.getCurrentTimeline()

        override fun getMediaItemCount(): Int = if (remote) castQueue.size else super.getMediaItemCount()
    }

    /** The content id the cast device holds for each of its ids, once it's been fetched. */
    private val fetched = mutableMapOf<Int, String>()
    private val asked = mutableListOf<Int>()
    private val lookup = object : CastQueueLookup {
        var changed: () -> Unit = {}
        override fun contentIdOf(itemId: Int): String? {
            asked += itemId
            return fetched[itemId]
        }
        override fun onChanged(changed: () -> Unit) {
            this.changed = changed
        }
    }

    private val sent = SentItems()
    private val details = CastQueueDetails(castDevice, sent, lookup)

    private fun song(n: Int) = MediaItem.Builder()
        .setMediaId("s$n")
        .setUri("https://vault.test/s$n")
        .setMediaMetadata(MediaMetadata.Builder().setTitle("Song $n").build())
        .build()

    private fun titles() = (0 until details.mediaItemCount).map {
        details.getMediaItemAt(
            it,
        ).mediaMetadata.title?.toString()
    }

    @After
    fun tearDown() {
        exo.release()
    }

    @Test
    fun `a song the cast device hasn't described comes back as the app sent it`() {
        (0..3).forEach { sent.remember(song(it)) }
        // Only the playing song (and the next) are described; the others are bare ids
        castQueue = listOf(100 to song(0), 101 to song(1), 102 to MediaItem.EMPTY, 103 to MediaItem.EMPTY)
        fetched += mapOf(102 to "s2", 103 to "s3")

        titles() shouldContainExactly listOf("Song 0", "Song 1", "Song 2", "Song 3")
        details.currentTimeline.getWindow(3, Timeline.Window()).mediaItem.mediaId shouldBe "s3"
    }

    @Test
    fun `one not fetched yet is asked for, and listeners hear once it's known`() {
        (0..2).forEach { sent.remember(song(it)) }
        castQueue = listOf(100 to song(0), 101 to MediaItem.EMPTY, 102 to MediaItem.EMPTY)
        val heard = mutableListOf<Int>()
        details.addListener(
            object : Player.Listener {
                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    heard += timeline.windowCount
                }
            },
        )

        titles() shouldContainExactly listOf("Song 0", null, null)
        asked.toSet() shouldBe setOf(101, 102)

        fetched += mapOf(101 to "s1", 102 to "s2")
        lookup.changed()

        heard shouldContainExactly listOf(3)
        titles() shouldContainExactly listOf("Song 0", "Song 1", "Song 2")
    }

    @Test
    fun `a song known once isn't asked for again`() {
        (0..1).forEach { sent.remember(song(it)) }
        castQueue = listOf(100 to song(0), 101 to MediaItem.EMPTY)
        fetched[101] = "s1"

        titles()
        titles()
        lookup.changed()
        titles()

        asked shouldContainExactly listOf(101)
    }

    @Test
    fun `nothing is heard when nothing new is known`() {
        sent.remember(song(0))
        castQueue = listOf(100 to song(0), 101 to MediaItem.EMPTY)
        var heard = 0
        details.addListener(
            object : Player.Listener {
                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    heard++
                }
            },
        )
        titles()

        lookup.changed()

        heard shouldBe 0
    }

    @Test
    fun `another app's song is marked as that, and the app's own songs aren't`() {
        sent.remember(song(0))
        castQueue = listOf(100 to song(7), 101 to song(0), 102 to MediaItem.EMPTY)
        fetched[102] = "someone-else"

        details.getMediaItemAt(0).mediaId shouldBe "s7"
        details.getMediaItemAt(0).sentFromAnotherApp shouldBe true
        details.getMediaItemAt(1).sentFromAnotherApp shouldBe false
        // A bare id that isn't one of this app's songs is no song of its own to show
        details.getMediaItemAt(2).sentFromAnotherApp shouldBe false
        details.currentTimeline.getWindow(0, Timeline.Window()).mediaItem.sentFromAnotherApp shouldBe true
    }

    @Test
    fun `with nothing sent yet (the app started again mid-cast), nothing there is marked as another app's`() {
        castQueue = listOf(100 to song(7))

        details.getMediaItemAt(0).sentFromAnotherApp shouldBe false
    }

    @Test
    fun `played here, the player's own queue is left alone and nothing is looked up`() {
        remote = false
        exo.setMediaItems(listOf(song(0), song(1)))

        titles() shouldContainExactly listOf("Song 0", "Song 1")
        asked shouldBe emptyList()
    }

    @Test
    fun `only the songs last sent are kept, however many were sent before`() {
        val sentItems = SentItems(limit = 3)
        (0..4).forEach { sentItems.remember(song(it)) }

        (0..4).map { sentItems["s$it"]?.mediaId } shouldContainExactly listOf(null, null, "s2", "s3", "s4")
    }
}

/** A cast device's queue as Media3's cast player has it: the cast device's id for each song. */
@OptIn(UnstableApi::class)
private class CastQueueTimeline(private val queue: List<Pair<Int, MediaItem>>) : Timeline() {
    override fun getWindowCount() = queue.size

    override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
        val (id, item) = queue[windowIndex]
        return window.set(
            id, item, null, 0L, 0L, 0L, true, false, null, 0L, 1_000_000L, windowIndex, windowIndex, 0L,
        )
    }

    override fun getPeriodCount() = queue.size

    override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period =
        period.set(queue[periodIndex].first, queue[periodIndex].first, periodIndex, 1_000_000L, 0L)

    override fun getIndexOfPeriod(uid: Any): Int = queue.indexOfFirst { it.first == uid }

    override fun getUidOfPeriod(periodIndex: Int): Any = queue[periodIndex].first
}
