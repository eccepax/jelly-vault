package org.jellyfin.mobile.player.queue

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.common.server.itemDownloadUrl
import org.jellyfin.mobile.player.deviceprofile.DeviceDecoders
import org.jellyfin.mobile.player.deviceprofile.DeviceProfileBuilder
import org.jellyfin.mobile.player.source.MediaSourceResolver
import org.jellyfin.mobile.player.source.RemoteJellyfinMediaSource
import org.jellyfin.mobile.testing.VaultTestServer
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.PlayMethod
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID

/**
 * The player's streams from the real Vault: what the server decides for a phone, and that the URL
 * the player builds ([streamOf]) really serves the book. Read-only.
 */
class AudiobookStreamingContractTest {

    private val api get() = VaultTestServer.apiClient

    /** A typical phone's audio decoders, as DeviceDecoders.fromDevice would find them. */
    private val phone = DeviceProfileBuilder("AudioVault contract tests", includeVideo = false) {
        DeviceDecoders(video = emptyMap(), audio = setOf("aac", "mp3", "flac", "opus", "vorbis"))
    }

    @BeforeEach
    fun setUp() {
        VaultTestServer.assumeAvailable()
    }

    @Test
    fun `an m4b audiobook plays directly on a phone, untouched`() = runBlocking<Unit> {
        resolve(audiobook("Philosopher")).playMethod shouldBe PlayMethod.DIRECT_PLAY
    }

    @Test
    fun `an mp3 audiobook plays directly on a phone, untouched`() = runBlocking<Unit> {
        resolve(audiobook("The Man in the Crowd")).playMethod shouldBe PlayMethod.DIRECT_PLAY
    }

    @Test
    fun `the URL the player streams from serves the book's audio`() = runBlocking<Unit> {
        val stream = api.streamOf(resolve(audiobook("Philosopher")))

        val response = get(stream.url)

        response.code shouldBe HttpURLConnection.HTTP_OK
        response.contentType shouldStartWith "audio/"
    }

    @Test
    fun `seeking fetches a byte range of the stream, which the server serves`() = runBlocking<Unit> {
        val stream = api.streamOf(resolve(audiobook("Philosopher")))

        val response = get(stream.url, range = "bytes=1000-1999")

        response.code shouldBe HttpURLConnection.HTTP_PARTIAL
        response.length shouldBe 1000L
    }

    @Test
    fun `a download is the very file the player streams, so positions match`() = runBlocking<Unit> {
        val id = audiobook("The Man in the Crowd")
        val streamed = get(api.streamOf(resolve(id)).url)
        val downloaded = get(itemDownloadUrl(requireNotNull(api.baseUrl), id, token = null))

        downloaded.code shouldBe HttpURLConnection.HTTP_OK
        downloaded.length shouldBe streamed.length
    }

    private suspend fun audiobook(nameContains: String): UUID = api.itemsApi.getItems(
        includeItemTypes = listOf(BaseItemKind.AUDIO_BOOK),
        recursive = true,
        searchTerm = nameContains,
        limit = 1,
    ).content.items.orEmpty().single().id!!

    private suspend fun resolve(itemId: UUID): RemoteJellyfinMediaSource =
        MediaSourceResolver(api).resolveMediaSource(itemId, deviceProfile = phone.getDeviceProfile()).getOrThrow()

    private class Response(val code: Int, val contentType: String, val length: Long)

    /** A GET as the player's data source makes it: the token in the Authorization header. Reads no body. */
    private fun get(url: String, range: String? = null): Response {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.setRequestProperty(
            "Authorization",
            AuthorizationHeaderBuilder.buildHeader(
                clientName = api.clientInfo.name,
                clientVersion = api.clientInfo.version,
                deviceId = api.deviceInfo.id,
                deviceName = api.deviceInfo.name,
                accessToken = api.accessToken,
            ),
        )
        range?.let { connection.setRequestProperty("Range", it) }
        return try {
            Response(connection.responseCode, connection.contentType.orEmpty(), connection.contentLengthLong)
        } finally {
            connection.disconnect()
        }
    }
}
