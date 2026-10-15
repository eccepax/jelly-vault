package org.jellyfin.mobile.downloads

import android.app.Application
import android.net.Uri
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** How the player tells which downloaded track, if any, a URL fetches. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AudioItemIdsTest {

    private val id = "4f1c2b7e-9d8a-4c3b-8e2f-1a0b9c8d7e6f"
    private val hexId = id.replace("-", "")

    private fun idOf(url: String) = audioItemIdOf(Uri.parse(url))

    @Test
    fun `a track's stream is its track, whatever the quality or token`() {
        idOf("http://vault/Audio/$hexId/universal?MaxStreamingBitrate=1&api_key=t") shouldBe id
        idOf("http://vault/Audio/$id/stream?static=true") shouldBe id
    }

    @Test
    fun `a track streamed as it is on the server (direct play or stream) is its track`() {
        idOf("http://vault/Videos/$hexId/stream?static=true&mediaSourceId=$hexId") shouldBe id
        idOf("http://vault/Videos/$id/stream.m4b?playSessionId=s") shouldBe id
    }

    @Test
    fun `a track's subtitles and other files aren't the track`() {
        idOf("http://vault/Videos/$hexId/$hexId/Subtitles/0/Stream.srt") shouldBe null
        idOf("http://vault/Videos/$hexId/Attachments/0") shouldBe null
    }

    @Test
    fun `a track's download url is its track`() {
        idOf("http://vault/Items/$hexId/Download") shouldBe id
    }

    @Test
    fun `artwork and anything else is not a track`() {
        idOf("http://vault/Items/$hexId/Images/Primary") shouldBe null
        idOf("http://vault/System/Info") shouldBe null
        idOf("http://vault/Audio/not-an-id/stream") shouldBe null
    }
}
