package org.jellyfin.mobile.music.catalog

import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.music.domain.StreamingQuality
import org.jellyfin.mobile.music.domain.Track
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.URI

/** The streaming-quality setting against the real Vault: a lower quality makes the server transcode. */
class JellyfinStreamingQualityContractTest {

    private var quality = StreamingQuality.ORIGINAL
    private lateinit var catalog: MusicCatalog

    @BeforeEach
    fun setUp() {
        VaultTestServer.assumeAvailable()
        catalog = JellyfinMusicCatalog(VaultTestServer.apiClient, streamingQuality = { quality })
    }

    private fun contentTypeOf(track: Track): String {
        val connection = URI(catalog.streamUrl(track.id)).toURL().openConnection() as HttpURLConnection
        connection.setRequestProperty("Range", "bytes=0-1023")
        return try {
            connection.responseCode shouldBeIn listOf(200, 206)
            connection.contentType
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun aFlacTrack(): Track = catalog.albums().firstNotNullOfOrNull { album ->
        catalog.albumTracks(album.id).firstOrNull()?.takeIf { contentTypeOf(it) == "audio/flac" }
    }.shouldNotBeNull()

    @Test
    fun `original quality streams flac untouched`() = runBlocking<Unit> {
        contentTypeOf(aFlacTrack()) shouldBe "audio/flac"
    }

    @Test
    fun `a lower quality transcodes flac to a compressed stream`() = runBlocking<Unit> {
        val flac = aFlacTrack()

        quality = StreamingQuality.LOW

        val transcoded = contentTypeOf(flac)
        transcoded shouldStartWith "audio/"
        transcoded shouldNotBe "audio/flac"
    }

    @Test
    fun `the quality is read for every new stream, so a change applies to the next song`() = runBlocking<Unit> {
        val flac = aFlacTrack()
        quality = StreamingQuality.LOW
        val low = catalog.streamUrl(flac.id)

        quality = StreamingQuality.ORIGINAL

        catalog.streamUrl(flac.id) shouldNotBe low
    }
}
