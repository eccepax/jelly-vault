package org.jellyfin.mobile.music.catalog

import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.music.domain.Track
import org.jellyfin.mobile.music.downloads.TrackCacheKey
import org.jellyfin.mobile.testing.VaultTestServer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder

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

    private fun containersAskedFor(url: String): List<String> = URI(url).rawQuery.split('&')
        .map { it.split('=', limit = 2) }
        .first { (name, _) -> name.equals("container", ignoreCase = true) }[1]
        .let { URLDecoder.decode(it, "UTF-8") }
        .split(',')

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

    @Test
    fun `cast and stream urls for a song share one cache key`() = runBlocking<Unit> {
        val track = aTrack()

        TrackCacheKey.trackIdOf(castUrlOf(track)) shouldBe track.id
    }
}
