package org.jellyfin.mobile.cast

import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What a cast device is sent for each item, and what comes back from it. */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
@Config(application = Application::class)
class VaultMediaItemConverterTest {

    private fun item(uri: String, mimeType: String? = null) = MediaItem.Builder()
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

    @Test
    fun `the cast device gets the stream url the app makes for it`() {
        val converter = VaultMediaItemConverter(castUri = { Uri.parse("$it?ApiKey=token") })

        val sent = converter.toMediaQueueItem(item("https://vault.test/Audio/1/universal"))

        sent.media?.contentUrl shouldBe "https://vault.test/Audio/1/universal?ApiKey=token"
    }

    @Test
    fun `an item without a type is sent with the fallback type`() {
        val converter = VaultMediaItemConverter(fallbackMimeType = "audio/mpeg")

        val sent = converter.toMediaQueueItem(item("https://vault.test/Audio/1/universal"))

        sent.media?.contentType shouldBe "audio/mpeg"
    }

    @Test
    fun `an item's own type wins over the fallback`() {
        val converter = VaultMediaItemConverter(fallbackMimeType = "audio/mpeg")

        val sent = converter.toMediaQueueItem(item("https://vault.test/live.m3u8", mimeType = "application/x-mpegURL"))

        sent.media?.contentType shouldBe "application/x-mpegURL"
    }

    @Test
    fun `an item the controller stripped of its uri is sent with its request uri`() {
        val converter = VaultMediaItemConverter()
        val stripped = MediaItem.Builder()
            .setMediaId("42")
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder().setMediaUri(
                    Uri.parse("https://vault.test/Audio/1/universal"),
                ).build(),
            )
            .build()

        val sent = converter.toMediaQueueItem(stripped)

        sent.media?.contentUrl shouldBe "https://vault.test/Audio/1/universal"
    }

    @Test
    fun `items come back from the cast device with their id, uri and metadata`() {
        val converter = VaultMediaItemConverter(castUri = { Uri.parse("$it?ApiKey=token") })

        val back = converter.toMediaItem(converter.toMediaQueueItem(item("https://vault.test/Audio/1/universal")))

        back.mediaId shouldBe "42"
        back.localConfiguration?.uri.toString() shouldBe "https://vault.test/Audio/1/universal?ApiKey=token"
        back.mediaMetadata.title.toString() shouldBe "Song"
        back.mediaMetadata.artist.toString() shouldBe "Artist"
        back.mediaMetadata.albumTitle.toString() shouldBe "Album"
        back.mediaMetadata.artworkUri.toString() shouldBe "https://vault.test/cover.jpg"
    }
}
