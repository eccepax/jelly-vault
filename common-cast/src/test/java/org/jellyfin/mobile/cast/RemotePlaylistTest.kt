package org.jellyfin.mobile.cast

import android.app.Application
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
    fun `the items kept are the ones sent, with their uris`() {
        playlist.setMediaItems(listOf(item("a")))

        playlist.items.single().localConfiguration?.uri.toString() shouldBe "https://vault.test/a"
    }
}
