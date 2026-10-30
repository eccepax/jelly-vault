package org.jellyfin.mobile.cast

import android.app.Application
import androidx.media3.common.MediaItem
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import io.kotest.matchers.shouldBe
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
}
