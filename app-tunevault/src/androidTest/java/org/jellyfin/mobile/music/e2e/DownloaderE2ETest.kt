package org.jellyfin.mobile.music.e2e

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadManager
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.music.catalog.JellyfinMusicCatalog
import org.jellyfin.mobile.music.domain.StreamingQuality
import org.jellyfin.mobile.music.domain.Track
import org.jellyfin.mobile.common.downloads.DownloadFiles
import org.jellyfin.mobile.common.downloads.ItemDownload
import org.jellyfin.mobile.common.downloads.ItemDownloads
import org.jellyfin.mobile.common.server.withAccessToken
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

/** The real downloader, download files and player against the real Vault: no UI involved. */
@UnstableApi
@DownloadsArea
class DownloaderE2ETest : LoggedInE2ETest() {

    private val downloader: ItemDownloads get() = koinGet()
    private val catalog: JellyfinMusicCatalog get() = koinGet()
    private val files: DownloadFiles get() = koinGet()
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
        composeRule.waitUntil(DOWNLOAD_TIMEOUT_MS) { stateOf(track.id)?.state == ItemDownload.State.DONE }
    }

    /** [track]'s downloaded file: its size and first bytes. */
    private fun readFromDevice(track: Track): Pair<Long, String> {
        val file = checkNotNull(onMain { downloader.fileFor(track.id) }) { "${track.id} isn't downloaded" }
        val head = ByteArray(4).also { bytes -> file.inputStream().use { it.read(bytes) } }
        return file.length() to String(head)
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

    private fun isOnDevice(track: Track) = files.fileOf(track.id).exists()

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
        assumeTrue("finished before it could be paused", partial.state != ItemDownload.State.DONE)
        Thread.sleep(PAUSE_MS)
        onMain { manager.resumeDownloads() }

        composeRule.waitUntil(DOWNLOAD_TIMEOUT_MS) { stateOf(track.id)?.state == ItemDownload.State.DONE }
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
        assertEquals(emptyList<String>(), files.root.list().orEmpty().toList())
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
        const val UNREACHABLE = "http://10.255.255.1:9"
    }
}
