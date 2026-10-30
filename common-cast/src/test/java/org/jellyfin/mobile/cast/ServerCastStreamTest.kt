package org.jellyfin.mobile.cast

import android.app.Application
import android.net.Uri
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** A song's stream as a cast device fetches it from the server. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
internal class ServerCastStreamTest {

    private val api = createJellyfin {
        context = RuntimeEnvironment.getApplication()
        clientInfo = ClientInfo("Test", "1.0")
        deviceInfo = DeviceInfo("test-device", "Test device")
    }.createApi(baseUrl = "https://vault.test", accessToken = "token")

    private fun query(stream: CastStream): Uri = Uri.parse(stream.url)

    @Test
    fun `a hi-res file is transcoded, since cast devices decode no more than 96 kHz`() {
        val url = query(serverCastStreamNow(api, ITEM_ID))

        url.getQueryParameter("maxAudioSampleRate") shouldBe "48000"
        url.getQueryParameter("maxAudioBitDepth") shouldBe "24"
    }

    @Test
    fun `nor at a lower quality, where everything is transcoded`() {
        val url = query(serverCastStreamNow(api, ITEM_ID, keepFormat = false, maxBitrate = 192_000))

        url.getQueryParameter("container") shouldBe null
        url.getQueryParameter("maxAudioSampleRate") shouldBe "48000"
        url.getQueryParameter("maxStreamingBitrate") shouldBe "192000"
    }

    @Test
    fun `files in a format cast devices play are asked for as they are`() {
        val url = query(serverCastStreamNow(api, ITEM_ID))

        url.getQueryParameters("container").flatMap { it.split(",") } shouldBe
            listOf("mp3", "flac", "aac", "m4a|aac", "m4b|aac", "mp4|aac", "ogg|vorbis", "ogg|opus", "opus|opus", "webm|opus")
        url.getQueryParameter("transcodingContainer") shouldBe "mp3"
    }

    private companion object {
        const val ITEM_ID = "0123456789abcdef0123456789abcdef"
    }
}
