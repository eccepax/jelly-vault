package org.jellyfin.mobile.player.playback

import android.app.Application
import android.net.Uri
import androidx.media3.common.MediaItem
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.player.session.withPlayableUri
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AudiobookMediaItemsTest {

    private val track = AudiobookTrack(
        id = "6b2c8f1e-0000-4000-8000-000000000001",
        title = "Chapter One",
        bookTitle = "The Book",
        author = "An Author",
        coverUrl = "https://vault.test/Items/1/Images/Primary",
        durationMs = 3_600_000L,
        chapters = listOf(Chapter(0L, "Opening"), Chapter(1_200_000L, "Middle")),
        fromDevice = true,
        container = "m4b",
        audioCodec = "aac",
    )

    @Test
    fun `a track survives the trip through a media item, as a controller sends it to the service`() {
        val item = track.toMediaItem(streamUrl = { "https://vault.test/Audio/$it/universal" })

        item.toAudiobookTrack() shouldBe track
    }

    @Test
    fun `its media id is the item id, and it streams from the url given`() {
        val item = track.toMediaItem(streamUrl = { "https://vault.test/Audio/$it/universal" })

        item.mediaId shouldBe track.id
        item.localConfiguration?.uri shouldBe Uri.parse("https://vault.test/Audio/${track.id}/universal")
    }

    @Test
    fun `the stream url is restored on the service side, where the controller stripped it`() {
        val sent = track.toMediaItem(streamUrl = { "https://vault.test/Audio/$it/universal" })
        val received = MediaItem.Builder()
            .setMediaId(sent.mediaId)
            .setMediaMetadata(sent.mediaMetadata)
            .setRequestMetadata(sent.requestMetadata)
            .build()

        received.withPlayableUri().localConfiguration?.uri shouldBe sent.localConfiguration?.uri
    }

    @Test
    fun `chapter starts come with the item, for moving between chapters`() {
        val item = track.toMediaItem(streamUrl = { "https://vault.test/$it" })

        item.mediaMetadata.chapterStartsMs shouldBe listOf(0L, 1_200_000L)
    }

    @Test
    fun `an item from elsewhere, without our extras, still reads as a track without chapters`() {
        val item = MediaItem.Builder().setMediaId("abc").build()

        item.toAudiobookTrack() shouldBe AudiobookTrack(id = "abc", title = "", durationMs = 0L)
    }
}
