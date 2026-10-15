package org.jellyfin.mobile.player.queue

import android.app.Application
import android.net.Uri
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContainIgnoringCase
import io.kotest.matchers.string.shouldStartWith
import io.mockk.every
import io.mockk.mockk
import org.jellyfin.mobile.downloads.audioItemIdOf
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.PlayMethod
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/** The URLs the player streams each play method from (built by the real SDK, no server needed). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class StreamUrlsTest {

    private val apiClient = OkHttpFactory().let { factory ->
        factory.create(
            baseUrl = "https://vault.example",
            accessToken = "secret-token",
            clientInfo = ClientInfo("AudioVault", "test"),
            deviceInfo = DeviceInfo("phone-1", "Phone"),
            httpClientOptions = HttpClientOptions(),
            socketConnectionFactory = factory,
        )
    }
    private val itemId = UUID.fromString("4f1c2b7e-9d8a-4c3b-8e2f-1a0b9c8d7e6f")

    // --- Direct play ------------------------------------------------------------------------------

    @Test
    fun `a file on the server is played straight from it, as one file`() {
        val stream = apiClient.streamOf(source(PlayMethod.DIRECT_PLAY))
        val uri = Uri.parse(stream.url)

        stream.url shouldStartWith "https://vault.example/"
        uri.getQueryParameter("static") shouldBe "true"
        uri.getQueryParameter("mediaSourceId") shouldBe "source-1"
        uri.getQueryParameter("playSessionId") shouldBe "session-1"
        uri.getQueryParameter("deviceId") shouldBe "phone-1"
        stream.format shouldBe StreamFormat.PROGRESSIVE
    }

    @Test
    fun `a direct-play track that's downloaded is recognised, so it plays from the device`() {
        val stream = apiClient.streamOf(source(PlayMethod.DIRECT_PLAY))

        audioItemIdOf(Uri.parse(stream.url)) shouldBe itemId.toString()
    }

    @Test
    fun `a remote file the server points to is played from there, as HLS when it's a playlist`() {
        apiClient.streamOf(
            source(PlayMethod.DIRECT_PLAY, protocol = MediaProtocol.HTTP, path = "https://cdn.example/book.m4b"),
        )
            .shouldBe(Stream("https://cdn.example/book.m4b", StreamFormat.PROGRESSIVE))
        apiClient.streamOf(source(PlayMethod.DIRECT_PLAY, protocol = MediaProtocol.HTTP, path = "https://cdn.example/book.M3U8"))
            .format shouldBe StreamFormat.HLS
    }

    @Test
    fun `other protocols can't be played`() {
        shouldThrow<IllegalArgumentException> {
            apiClient.streamOf(source(PlayMethod.DIRECT_PLAY, protocol = MediaProtocol.RTSP))
        }
    }

    // --- Direct stream ----------------------------------------------------------------------------

    @Test
    fun `a direct stream asks for the file's own container`() {
        val stream = apiClient.streamOf(source(PlayMethod.DIRECT_STREAM, container = "m4b"))

        Uri.parse(stream.url).lastPathSegment shouldBe "stream.m4b"
        stream.format shouldBe StreamFormat.PROGRESSIVE
    }

    @Test
    fun `a direct-stream track that's downloaded is recognised, so it plays from the device`() {
        val stream = apiClient.streamOf(source(PlayMethod.DIRECT_STREAM, container = "m4b"))

        audioItemIdOf(Uri.parse(stream.url)) shouldBe itemId.toString()
    }

    // --- Transcode --------------------------------------------------------------------------------

    @Test
    fun `a transcode plays the server's transcoding URL, on the server`() {
        val stream = apiClient.streamOf(
            source(
                PlayMethod.TRANSCODE,
                transcodingUrl = "/Audio/$itemId/main.m3u8?PlaySessionId=session-1",
                subProtocol = MediaStreamProtocol.HLS,
            ),
        )

        stream.url shouldStartWith "https://vault.example/Audio/$itemId/main.m3u8"
        stream.format shouldBe StreamFormat.HLS
    }

    @Test
    fun `a progressive transcode is read as one file`() {
        apiClient.streamOf(
            source(PlayMethod.TRANSCODE, transcodingUrl = "/Audio/$itemId/stream.mp3", subProtocol = MediaStreamProtocol.HTTP),
        ).format shouldBe StreamFormat.PROGRESSIVE
    }

    // --- Every play method ------------------------------------------------------------------------

    @Test
    fun `the access token never goes in the URL`() {
        val urls = listOf(
            apiClient.streamOf(source(PlayMethod.DIRECT_PLAY)).url,
            apiClient.streamOf(source(PlayMethod.DIRECT_STREAM, container = "m4b")).url,
        )

        urls.forEach { it shouldNotContainIgnoringCase "secret-token" }
    }

    @Suppress("LongParameterList")
    private fun source(
        playMethod: PlayMethod,
        protocol: MediaProtocol = MediaProtocol.FILE,
        path: String? = "/books/Dune/01.m4b",
        container: String? = null,
        transcodingUrl: String? = null,
        subProtocol: MediaStreamProtocol = MediaStreamProtocol.HTTP,
    ): JellyfinMediaSource {
        val info = mockk<MediaSourceInfo> {
            every { this@mockk.protocol } returns protocol
            every { this@mockk.path } returns path
            every { this@mockk.container } returns container
            every { this@mockk.transcodingUrl } returns transcodingUrl
            every { transcodingSubProtocol } returns subProtocol
        }
        return mockk {
            every { itemId } returns this@StreamUrlsTest.itemId
            every { id } returns "source-1"
            every { playSessionId } returns "session-1"
            every { this@mockk.playMethod } returns playMethod
            every { sourceInfo } returns info
        }
    }
}
