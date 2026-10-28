package org.jellyfin.mobile.music.catalog

import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.cast.CastStream
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
 * the type it's sent as. What each file is comes from the server, when it's cast.
 */
class JellyfinCastStreamContractTest {

    private lateinit var catalog: JellyfinMusicCatalog

    @BeforeEach
    fun setUp() {
        VaultTestServer.assumeAvailable()
        catalog = JellyfinMusicCatalog(VaultTestServer.apiClient)
    }

    private suspend fun aTrack() = catalog.albumTracks(catalog.albums().first().id).first()

    private suspend fun castStreamOf(track: Track, fromMs: Long = 0L, from: JellyfinMusicCatalog = catalog): CastStream =
        from.castSource(track.id).streamFrom(fromMs)

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

    private fun parameter(url: String, name: String): String? = URI(url).rawQuery.split('&')
        .map { it.split('=', limit = 2) }
        .firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }
        ?.let { URLDecoder.decode(it[1], "UTF-8") }

    /** A song whose file is a flac, as the server sends it when asked for it as it is. */
    private suspend fun aFlacTrack(): Track = catalog.albums().firstNotNullOfOrNull { album ->
        catalog.albumTracks(album.id).firstOrNull { contentTypeOf(catalog.streamUrl(it.id)) == "audio/flac" }
    }.shouldNotBeNull()

    @Test
    fun `the cast url serves the song on its own`() = runBlocking<Unit> {
        val stream = castStreamOf(aTrack())

        contentTypeOf(stream.url) shouldBe stream.mimeType
    }

    @Test
    fun `a flac song is cast as it is, the server having said what it is, within what cast devices decode`() =
        runBlocking<Unit> {
            val stream = castStreamOf(aFlacTrack())

            stream.mimeType shouldBe "audio/flac"
            containersAskedFor(stream.url) shouldBe listOf("flac")
            parameter(stream.url, "maxAudioSampleRate") shouldBe "96000"
            parameter(stream.url, "maxAudioBitDepth") shouldBe "24"
            // So that whatever the server sends is the type the cast device was told
            parameter(stream.url, "transcodingContainer") shouldBe "flac"
            contentTypeOf(stream.url) shouldBe "audio/flac"
        }

    @Test
    fun `containers that can hold what cast devices can't play are asked for only with a codec they can`() =
        runBlocking<Unit> {
            // m4a and mp4 also hold ALAC, which cast devices can't play
            containersAskedFor(castStreamOf(aTrack()).url) shouldNotContainAnyOf listOf("m4a", "mp4")
        }

    @Test
    fun `a song the server can't say anything of is cast as an mp3 transcode, from where playback is`() =
        runBlocking<Unit> {
            val stream = catalog.castSource(UUID.randomUUID().toString()).streamFrom(30_000L)

            stream.mimeType shouldBe "audio/mpeg"
            stream.seekable shouldBe false
            stream.startMs shouldBe 30_000L
        }

    @Test
    fun `below the original quality, a song is cast as an mp3 the server can make`() = runBlocking<Unit> {
        val lowQuality = JellyfinMusicCatalog(VaultTestServer.apiClient, streamingQuality = { StreamingQuality.LOW })

        val stream = castStreamOf(aFlacTrack(), from = lowQuality)

        stream.mimeType shouldBe "audio/mpeg"
        contentTypeOf(stream.url) shouldBe "audio/mpeg"
    }

    @Test
    fun `a song cast as it is starts at its start wherever playback is, and the cast device seeks in it`() =
        runBlocking<Unit> {
            val stream = castStreamOf(aFlacTrack(), fromMs = 30_000L)

            stream.startMs shouldBe 0L
            stream.seekable shouldBe true
            parameter(stream.url, "startTimeTicks") shouldBe null
        }

    @Test
    fun `a transcoded song is cast from where playback is, and starts there`() = runBlocking<Unit> {
        val lowQuality = JellyfinMusicCatalog(VaultTestServer.apiClient, streamingQuality = { StreamingQuality.LOW })

        val stream = castStreamOf(aFlacTrack(), fromMs = 30_000L, from = lowQuality)

        stream.startMs shouldBe 30_000L
        stream.seekable shouldBe false
        parameter(stream.url, "startTimeTicks") shouldBe "300000000"
        contentTypeOf(stream.url) shouldBe "audio/mpeg"
    }

    @Test
    fun `cast and stream urls for a song share one cache key`() = runBlocking<Unit> {
        val track = aTrack()

        TrackCacheKey.trackIdOf(castStreamOf(track).url) shouldBe track.id
    }
}
