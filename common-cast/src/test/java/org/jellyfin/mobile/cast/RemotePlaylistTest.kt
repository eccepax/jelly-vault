package org.jellyfin.mobile.cast

import android.app.Application
import android.os.Bundle
import android.os.Looper
import androidx.annotation.OptIn
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

    private fun settle() {
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
    fun `the items kept are the ones sent, with their uris`() {
        playlist.setMediaItems(listOf(item("a")))

        playlist.items.single().localConfiguration?.uri.toString() shouldBe "https://vault.test/a"
    }
}
