package org.jellyfin.mobile.music.catalog

import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.music.downloads.TrackCacheKey
import org.jellyfin.mobile.testing.VaultTestServer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder

/**
 * What a cast device is sent: the Default Media Receiver fetches the song itself, so the url works
 * on its own, and asks only for what cast devices play (ALAC, say, comes as MP3 instead).
 */
class JellyfinCastStreamContractTest {

    private lateinit var catalog: JellyfinMusicCatalog

    @BeforeEach
    fun setUp() {
        VaultTestServer.assumeAvailable()
        catalog = JellyfinMusicCatalog(VaultTestServer.apiClient)
    }

    private suspend fun aTrackId() = catalog.albumTracks(catalog.albums().first().id).first().id

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
        contentTypeOf(catalog.castStreamUrl(aTrackId())).shouldNotBeNull()
    }

    @Test
    fun `a flac song is cast as it is`() = runBlocking<Unit> {
        val flac = catalog.albums().firstNotNullOfOrNull { album ->
            val track = catalog.albumTracks(album.id).firstOrNull()
            track?.takeIf { contentTypeOf(catalog.streamUrl(it.id)) == "audio/flac" }
        }.shouldNotBeNull()

        contentTypeOf(catalog.castStreamUrl(flac.id)) shouldBe "audio/flac"
    }

    @Test
    fun `containers that can hold what cast devices can't play are asked for only with a codec they can`() =
        runBlocking<Unit> {
            // m4a and mp4 also hold ALAC, which cast devices can't play
            containersAskedFor(catalog.castStreamUrl(aTrackId())) shouldNotContainAnyOf listOf("m4a", "mp4")
        }

    @Test
    fun `cast and stream urls for a song share one cache key`() = runBlocking<Unit> {
        val id = aTrackId()

        TrackCacheKey.trackIdOf(catalog.castStreamUrl(id)) shouldBe id
    }
}
