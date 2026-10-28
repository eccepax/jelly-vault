package org.jellyfin.mobile.cast

import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What a cast device is sent for each entry, and what comes back from it. */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
@Config(application = Application::class)
class CastQueueItemsTest {

    private fun item(uri: String? = "https://vault.test/Audio/1/universal", mimeType: String? = null) =
        MediaItem.Builder()
            .setMediaId("42")
            .setUri(uri)
            .setMimeType(mimeType)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Song")
                    .setArtist("Artist")
                    .setAlbumTitle("Album")
                    .setArtworkUri(Uri.parse("https://vault.test/cover.jpg"))
                    .build(),
            )
            .build()

    private val stream = CastStream("https://vault.test/cast/42?ApiKey=token", "audio/flac")

    @Test
    fun `the cast device fetches the stream the app chose, as its type`() {
        val sent = CastQueueItems.queueItemOf(SentItem("key", item(), stream))

        sent.media?.contentUrl shouldBe "https://vault.test/cast/42?ApiKey=token"
        sent.media?.contentType shouldBe "audio/flac"
        sent.media?.streamType shouldBe MediaInfo.STREAM_TYPE_BUFFERED
    }

    @Test
    fun `each goes with its entry's key, which comes back`() {
        val sent = CastQueueItems.queueItemOf(SentItem("key", item(), stream))

        CastQueueItems.keyOf(sent) shouldBe "key"
    }

    @Test
    fun `another app's item has no key`() {
        val theirs = MediaQueueItem.Builder(MediaInfo.Builder("https://elsewhere.test/a").build()).build()

        CastQueueItems.keyOf(theirs).shouldBeNull()
        CastQueueItems.mediaItemOf(theirs).shouldBeNull()
    }

    @Test
    fun `items come back as the app gave them, with their own uri rather than the cast stream's`() {
        val back = CastQueueItems.mediaItemOf(CastQueueItems.queueItemOf(SentItem("key", item(), stream)))!!

        back.mediaId shouldBe "42"
        back.localConfiguration?.uri.toString() shouldBe "https://vault.test/Audio/1/universal"
        back.mediaMetadata.title.toString() shouldBe "Song"
        back.mediaMetadata.artist.toString() shouldBe "Artist"
        back.mediaMetadata.albumTitle.toString() shouldBe "Album"
        back.mediaMetadata.artworkUri.toString() shouldBe "https://vault.test/cover.jpg"
    }

    @Test
    fun `an item that can't say how to play it is still sent, rather than crash the playback service`() {
        // As another controller (system UI, Android Auto) can add one: no uri, nothing to restore it from
        val bare = MediaItem.Builder().setMediaId("42").build()

        val sent = CastQueueItems.queueItemOf(SentItem("key", bare, CastStream("42", "audio/mpeg")))

        CastQueueItems.mediaItemOf(sent)?.mediaId shouldBe "42"
    }

    @Test
    fun `a live item goes as a live stream`() {
        val sent = CastQueueItems.queueItemOf(SentItem("key", item(), stream), live = true)

        sent.media?.streamType shouldBe MediaInfo.STREAM_TYPE_LIVE
    }

    @Test
    fun `with nothing from the app, a song goes from its own uri made fit to fetch, as its own type or the fallback`() {
        val token = { uri: Uri -> Uri.parse("$uri?ApiKey=token") }

        defaultCastStream(item(), token, "audio/mpeg") shouldBe
            CastStream("https://vault.test/Audio/1/universal?ApiKey=token", "audio/mpeg")
        defaultCastStream(item("https://vault.test/live.m3u8", "application/x-mpegURL"), token, "audio/mpeg")
            .mimeType shouldBe "application/x-mpegURL"
    }

    @Test
    fun `with nothing from the app, an item the controller stripped of its uri goes from its request uri`() {
        val stripped = MediaItem.Builder()
            .setMediaId("42")
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder().setMediaUri(
                    Uri.parse("https://vault.test/Audio/1/universal"),
                ).build(),
            )
            .build()

        defaultCastStream(stripped, { it }, "audio/mpeg").url shouldBe "https://vault.test/Audio/1/universal"
    }
}
