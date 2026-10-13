package org.jellyfin.mobile.music.e2e

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.offline.DownloadManager
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.music.catalog.JellyfinMusicCatalog
import org.jellyfin.mobile.music.domain.StreamingQuality
import org.jellyfin.mobile.music.domain.Track
import org.jellyfin.mobile.music.downloads.TrackCacheKey
import org.jellyfin.mobile.music.downloads.TrackDownload
import org.jellyfin.mobile.music.downloads.TrackDownloader
import org.jellyfin.mobile.music.downloads.withAccessToken
import org.jellyfin.mobile.music.playback.PlaybackController
import org.jellyfin.sdk.api.client.ApiClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URI

/** The real downloader, cache and player against the real Vault: no UI involved. */
@UnstableApi
@DownloadsArea
class DownloaderE2ETest : LoggedInE2ETest() {

    private val downloader: TrackDownloader get() = koinGet()
    private val catalog: JellyfinMusicCatalog get() = koinGet()
    private val cache: Cache get() = koinGet()
    private val apiClient: ApiClient get() = koinGet()
    private var realBaseUrl: String? = null

    @After
    fun removeDownloads() {
        realBaseUrl?.let { onMain { apiClient.update(baseUrl = it) } }
        onMain { koinGet<PlaybackController>().stop() }
        onMain { downloader.downloads.value.keys.forEach(downloader::remove) }
        composeRule.waitUntil(DOWNLOAD_TIMEOUT_MS) { onMain { downloader.downloads.value.isEmpty() } }
    }

    private fun signedIn() {
        connectAndLogin()
        waitForComposeText("Recently added")
    }

    private fun purpleBird(): List<Track> = runBlocking {
        catalog.albumTracks(catalog.albums().first { it.title == "The Purple Bird" }.id)
    }

    private fun stateOf(trackId: String) = onMain { downloader.downloads.value[trackId] }

    private fun downloadAndWait(track: Track) {
        onMain { downloader.download(track.id, catalog.downloadUri(track.id, StreamingQuality.ORIGINAL)) }
        composeRule.waitUntil(DOWNLOAD_TIMEOUT_MS) { stateOf(track.id)?.state == TrackDownload.State.DONE }
    }

    /** Reads [track] from the device only (no network, no writing): its size and first bytes. */
    private fun readFromDevice(track: Track): Pair<Long, String> {
        val source = CacheDataSource.Factory()
            .setCache(cache)
            .setCacheKeyFactory(TrackCacheKey.factory)
            .setCacheWriteDataSinkFactory(null)
            .createDataSource()
        source.open(DataSpec(Uri.parse(catalog.downloadUri(track.id, StreamingQuality.ORIGINAL))))
        return try {
            val buffer = ByteArray(BUFFER_BYTES)
            var total = 0L
            var head = ""
            while (true) {
                val read = source.read(buffer, 0, buffer.size)
                if (read == C.RESULT_END_OF_INPUT) break
                if (total == 0L) head = String(buffer, 0, minOf(read, 4))
                total += read
            }
            total to head
        } finally {
            source.close()
        }
    }

    private fun serverLength(track: Track): Long {
        val uri = withAccessToken(catalog.downloadUri(track.id, StreamingQuality.ORIGINAL), apiClient.accessToken)
        val connection = URI(uri).toURL().openConnection() as HttpURLConnection
        return try {
            connection.contentLengthLong
        } finally {
            connection.disconnect()
        }
    }

    private fun isOnDevice(track: Track) = "track:${track.id}" in cache.keys

    @Test
    fun aSongDownloadsWholeAndReadsBackFromTheDeviceAlone() {
        signedIn()
        val track = purpleBird().first()

        downloadAndWait(track)

        val (size, head) = readFromDevice(track)
        assertEquals(serverLength(track), size)
        assertEquals(size, stateOf(track.id)!!.bytesDownloaded)
        assertEquals("fLaC", head)
    }

    @Test
    fun aDownloadPausedPartWayResumesAndFinishesTheWholeFile() {
        signedIn()
        val track = runBlocking { catalog.tracks() }.maxBy { it.durationMs }
        val manager: DownloadManager = koinGet()

        onMain { downloader.download(track.id, catalog.downloadUri(track.id, StreamingQuality.ORIGINAL)) }
        composeRule.waitUntil(DOWNLOAD_TIMEOUT_MS) { (stateOf(track.id)?.bytesDownloaded ?: 0L) > 0L }
        onMain { manager.pauseDownloads() }
        val partial = stateOf(track.id)!!
        assumeTrue("finished before it could be paused", partial.state != TrackDownload.State.DONE)
        Thread.sleep(PAUSE_MS)
        onMain { manager.resumeDownloads() }

        composeRule.waitUntil(DOWNLOAD_TIMEOUT_MS) { stateOf(track.id)?.state == TrackDownload.State.DONE }
        assertEquals(serverLength(track), readFromDevice(track).first)
    }

    @Test
    fun aDownloadedSongPlaysWithTheServerUnreachable() {
        signedIn()
        val track = purpleBird().first()
        downloadAndWait(track)
        val playback: PlaybackController = koinGet()

        // Every stream URL now points nowhere, so only the device copy can play.
        realBaseUrl = apiClient.baseUrl
        onMain { apiClient.update(baseUrl = UNREACHABLE) }
        onMain { playback.play(listOf(track)) }

        composeRule.waitUntil(PLAY_TIMEOUT_MS) { onMain { playback.state.value.isPlaying && playback.positionMs > 2_000L } }
    }

    @Test
    fun removingADownloadFreesItsSpace() {
        signedIn()
        val track = purpleBird().first()
        downloadAndWait(track)
        assertTrue(isOnDevice(track))

        onMain { downloader.remove(track.id) }

        composeRule.waitUntil(DOWNLOAD_TIMEOUT_MS) { stateOf(track.id) == null && !isOnDevice(track) }
        assertEquals(0L, cache.cacheSpace)
    }

    @Test
    fun aStreamedSongIsNotKeptOnTheDevice() {
        signedIn()
        val track = purpleBird()[1]
        val playback: PlaybackController = koinGet()

        onMain { playback.play(listOf(track)) }
        composeRule.waitUntil(PLAY_TIMEOUT_MS) { onMain { playback.state.value.isPlaying && playback.positionMs > 2_000L } }

        assertFalse(isOnDevice(track))
    }

    private companion object {
        const val DOWNLOAD_TIMEOUT_MS = 120_000L
        const val PLAY_TIMEOUT_MS = 30_000L
        const val PAUSE_MS = 1_500L
        const val BUFFER_BYTES = 64 * 1024
        const val UNREACHABLE = "http://10.255.255.1:9"
    }
}
