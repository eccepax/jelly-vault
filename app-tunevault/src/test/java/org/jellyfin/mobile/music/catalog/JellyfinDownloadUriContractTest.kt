package org.jellyfin.mobile.music.catalog

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.music.domain.StreamingQuality
import org.jellyfin.mobile.music.downloads.TrackCacheKey
import org.jellyfin.mobile.music.downloads.withAccessToken
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.URI

/**
 * Download URIs are stored by the downloader for as long as a download lasts, so they carry no
 * token: one is added to each request as it's made (see [withAccessToken]). A stored token would
 * break every pending download the moment you signed out and back in.
 */
class JellyfinDownloadUriContractTest {

    private lateinit var catalog: JellyfinMusicCatalog

    @BeforeEach
    fun setUp() {
        VaultTestServer.assumeAvailable()
        catalog = JellyfinMusicCatalog(VaultTestServer.apiClient)
    }

    private suspend fun aTrackId() = catalog.albumTracks(catalog.albums().first().id).first().id

    private fun statusAndType(uri: String): Pair<Int, String?> {
        val connection = URI(uri).toURL().openConnection() as HttpURLConnection
        connection.setRequestProperty("Range", "bytes=0-1023")
        return try {
            connection.responseCode to connection.contentType
        } finally {
            connection.disconnect()
        }
    }

    @Test
    fun `a download uri holds no token, and the server refuses it alone`() = runBlocking<Unit> {
        val uri = catalog.downloadUri(aTrackId(), StreamingQuality.ORIGINAL)

        uri shouldNotContain "api_key"
        statusAndType(uri).first shouldBe 401
    }

    @Test
    fun `with the current token added at request time it serves the song`() = runBlocking<Unit> {
        val uri = catalog.downloadUri(aTrackId(), StreamingQuality.ORIGINAL)
        val authed = withAccessToken(uri, VaultTestServer.apiClient.accessToken)

        val (status, type) = statusAndType(authed)
        status shouldBe 206
        type.orEmpty() shouldStartWith "audio/"
    }

    @Test
    fun `download and stream uris for a song share one cache key`() = runBlocking<Unit> {
        val id = aTrackId()

        TrackCacheKey.trackIdOf(catalog.downloadUri(id, StreamingQuality.LOW)) shouldBe id
        TrackCacheKey.trackIdOf(catalog.streamUrl(id)) shouldBe id
    }
}
