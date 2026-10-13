package org.jellyfin.mobile.music.playback

import android.app.Application
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jellyfin.mobile.music.domain.PlayQueue
import org.jellyfin.mobile.music.domain.track
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MediaItemsTest {

    private val streamUrl = { trackId: String -> "https://vault.test/stream/$trackId" }

    @Test
    fun `media id is the queue entry's uid, so two copies of a track stay distinct`() {
        val queue = PlayQueue.of(listOf(track("a"), track("a")))

        val items = queue.entries.map { it.toMediaItem(streamUrl) }

        items[0].mediaId shouldNotBe items[1].mediaId
        items.map { it.queueUid } shouldBe queue.entries.map { it.uid }
    }

    @Test
    fun `carries the track's metadata`() {
        val entry = PlayQueue.of(listOf(track("a").copy(title = "London May", trackNumber = 2))).entries.single()

        val metadata = entry.toMediaItem(streamUrl).mediaMetadata

        metadata.title shouldBe "London May"
        metadata.artist shouldBe "Artist"
        metadata.albumTitle shouldBe "Album"
        metadata.trackNumber shouldBe 2
    }

    @Test
    fun `carries the cover as artwork for the notification and lock screen`() {
        val entry = PlayQueue.of(listOf(track("a").copy(coverUrl = "https://vault.test/cover/a"))).entries.single()

        entry.toMediaItem(streamUrl).mediaMetadata.artworkUri shouldBe Uri.parse("https://vault.test/cover/a")
    }

    @Test
    fun `carries the stream url for the player and for the session`() {
        val entry = PlayQueue.of(listOf(track("a"))).entries.single()

        val item = entry.toMediaItem(streamUrl)

        item.localConfiguration?.uri shouldBe Uri.parse("https://vault.test/stream/a")
        item.requestMetadata.mediaUri shouldBe Uri.parse("https://vault.test/stream/a")
    }

    @Test
    fun `the session restores a uri that was stripped on the way from the controller`() {
        // MediaController -> MediaSession drops localConfiguration; the uri survives in requestMetadata.
        val stripped = MediaItem.Builder()
            .setMediaId("7")
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse("https://vault.test/stream/a")).build(),
            )
            .setMediaMetadata(MediaMetadata.Builder().setTitle("London May").build())
            .build()

        val resolved = stripped.withPlayableUri()

        resolved.localConfiguration?.uri shouldBe Uri.parse("https://vault.test/stream/a")
        resolved.mediaId shouldBe "7"
        resolved.mediaMetadata.title shouldBe "London May"
    }

    @Test
    fun `a media item that already has a uri is left as is`() {
        val item = PlayQueue.of(listOf(track("a"))).entries.single().toMediaItem(streamUrl)

        item.withPlayableUri() shouldBe item
    }
}
