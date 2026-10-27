package org.jellyfin.mobile.music.catalog

import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.music.domain.StreamingQuality
import org.jellyfin.mobile.music.domain.Track
import org.jellyfin.mobile.music.downloads.TrackCacheKey
import org.jellyfin.mobile.testing.VaultTestServer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import java.util.UUID

/**
 * What a cast device is sent: the Default Media Receiver fetches the song itself, so the url works
 * on its own, asks only for what cast devices play (ALAC, say, comes as MP3 instead), and serves
 * the type it's sent as.
 */
class JellyfinCastStreamContractTest {

    private lateinit var catalog: JellyfinMusicCatalog

    @BeforeEach
    fun setUp() {
        VaultTestServer.assumeAvailable()
        catalog = JellyfinMusicCatalog(VaultTestServer.apiClient)
    }

    private suspend fun aTrack() = catalog.albumTracks(catalog.albums().first().id).first()

    private fun castUrlOf(track: Track) = catalog.castStream(track.id, track.container).url

    private fun contentTypeOf(url: String): String? {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.setRequestProperty("Range", "bytes=0-1023")
        return try {
            connection.responseCode shouldBeIn listOf(200, 206)
            connection.contentType
        } finally {
            connection.disconnect()
        }
    }

    /** Every container asked for, whether the url lists them in one parameter or repeats it. */
    private fun containersAskedFor(url: String): List<String> = URI(url).rawQuery.split('&')
        .map { it.split('=', limit = 2) }
        .filter { (name, _) -> name.equals("container", ignoreCase = true) }
        .flatMap { URLDecoder.decode(it[1], "UTF-8").split(',') }

    @Test
    fun `the cast url serves the song on its own`() = runBlocking<Unit> {
        val track = aTrack()
        val stream = catalog.castStream(track.id, track.container)

        contentTypeOf(stream.url) shouldBe stream.mimeType
    }

    @Test
    fun `a flac song is cast as it is`() = runBlocking<Unit> {
        val flac = catalog.albums().firstNotNullOfOrNull { album ->
            val track = catalog.albumTracks(album.id).firstOrNull()
            track?.takeIf { contentTypeOf(catalog.streamUrl(it.id)) == "audio/flac" }
        }.shouldNotBeNull()

        flac.container shouldBe "flac"
        catalog.castStream(flac.id, flac.container).mimeType shouldBe "audio/flac"
        contentTypeOf(castUrlOf(flac)) shouldBe "audio/flac"
    }

    @Test
    fun `containers that can hold what cast devices can't play are asked for only with a codec they can`() =
        runBlocking<Unit> {
            // m4a and mp4 also hold ALAC, which cast devices can't play
            containersAskedFor(castUrlOf(aTrack())) shouldNotContainAnyOf listOf("m4a", "mp4")
        }

    private fun parameter(url: String, name: String): String? = URI(url).rawQuery.split('&')
        .map { it.split('=', limit = 2) }
        .firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }
        ?.let { URLDecoder.decode(it[1], "UTF-8") }

    @Test
    fun `a lossless song comes as flac within what cast devices decode, transcoded down if it's more`() {
        listOf("flac", "wav").forEach { container ->
            val stream = catalog.castStream(UUID.randomUUID().toString(), container)

            stream.mimeType shouldBe "audio/flac"
            containersAskedFor(stream.url) shouldBe listOf("flac")
            parameter(stream.url, "maxAudioSampleRate") shouldBe "96000"
            parameter(stream.url, "maxAudioBitDepth") shouldBe "24"
            // So that whatever the server sends is the type the cast device was told
            parameter(stream.url, "transcodingContainer") shouldBe "flac"
        }
    }

    @Test
    fun `a container that holds several codecs is asked for only with those cast devices play, else made one`() {
        val stream = catalog.castStream(UUID.randomUUID().toString(), "ogg")

        stream.mimeType shouldBe "audio/ogg"
        containersAskedFor(stream.url) shouldBe listOf("ogg|vorbis", "ogg|opus")
        parameter(stream.url, "transcodingContainer") shouldBe "ogg"
        parameter(stream.url, "audioCodec") shouldBe "opus"
    }

    @Test
    fun `below the original quality, a song is cast as an mp3 the server can make`() = runBlocking<Unit> {
        val lowQuality = JellyfinMusicCatalog(VaultTestServer.apiClient, streamingQuality = { StreamingQuality.LOW })
        val flac = catalog.albums().firstNotNullOfOrNull { album ->
            catalog.albumTracks(album.id).firstOrNull { it.container == "flac" }
        }.shouldNotBeNull()

        val stream = lowQuality.castStream(flac.id, flac.container)

        stream.mimeType shouldBe "audio/mpeg"
        contentTypeOf(stream.url) shouldBe "audio/mpeg"
    }

    @Test
    fun `cast and stream urls for a song share one cache key`() = runBlocking<Unit> {
        val track = aTrack()

        TrackCacheKey.trackIdOf(castUrlOf(track)) shouldBe track.id
    }
}
