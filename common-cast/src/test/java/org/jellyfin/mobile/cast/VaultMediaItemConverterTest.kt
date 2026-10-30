package org.jellyfin.mobile.cast

import android.app.Application
import androidx.media3.common.MediaItem
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import io.kotest.matchers.shouldBe
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Items as the cast device gets them. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class VaultMediaItemConverterTest {

    private val untyped = MediaItem.Builder().setMediaId("item").setUri("https://vault/stream").build()
    private val stream = CastStream("https://vault/cast", "application/x-mpegURL")

    @Test
    fun `a song with no type goes as music, so the receiver shows its cover and artist`() {
        val sent = VaultMediaItemConverter(castStream = { stream }).toMediaQueueItem(untyped)

        sent.media?.metadata?.mediaType shouldBe MediaMetadata.MEDIA_TYPE_MUSIC_TRACK
        sent.media?.streamType shouldBe MediaInfo.STREAM_TYPE_BUFFERED
    }

    @Test
    fun `a live channel goes as video, which the receiver plays rather than showing a music screen`() {
        val sent = VaultMediaItemConverter(castStream = { stream }, live = true).toMediaQueueItem(untyped)

        sent.media?.metadata?.mediaType shouldBe MediaMetadata.MEDIA_TYPE_MOVIE
        sent.media?.streamType shouldBe MediaInfo.STREAM_TYPE_LIVE
    }

    @Test
    fun `each song sent is remembered as it was sent`() {
        val converter = VaultMediaItemConverter(castStream = { stream })

        converter.toMediaQueueItem(untyped)

        converter.sent["item"] shouldBe untyped
    }

    @Test
    fun `a song the cast device gave only its id for comes back empty, so what's known of it is kept`() {
        // Media3 keeps a song's details from before only when the converter has nothing for it
        val undescribed = MediaQueueItem(JSONObject("""{"itemId": 7}"""))

        undescribed.media shouldBe null
        VaultMediaItemConverter(castStream = { stream }).toMediaItem(undescribed) shouldBe MediaItem.EMPTY
    }
}
