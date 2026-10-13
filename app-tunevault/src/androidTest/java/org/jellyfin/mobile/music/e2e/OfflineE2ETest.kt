package org.jellyfin.mobile.music.e2e

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.pressBack
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.music.catalog.JellyfinMusicCatalog
import org.jellyfin.mobile.music.domain.Album
import org.jellyfin.mobile.music.domain.Track
import org.jellyfin.mobile.music.downloads.DownloadRepository
import org.jellyfin.mobile.music.downloads.DownloadStatus
import org.jellyfin.mobile.music.offline.ServerConnectivity
import org.jellyfin.mobile.music.playback.OfflinePlayReporter
import org.jellyfin.mobile.music.playback.PlaybackController
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.UpdateUserItemDataDto
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.jellyfin.sdk.model.serializer.toUUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Downloads through the UI, then the Vault made unreachable: the app carries on from the device.
 * Uses the library's smallest album, chosen at run time, so downloads take seconds, not minutes.
 */
@DownloadsArea
class OfflineE2ETest : LoggedInE2ETest() {

    private val repository: DownloadRepository get() = koinGet()
    private val apiClient: ApiClient get() = koinGet()
    private val catalog: JellyfinMusicCatalog get() = koinGet()
    private var realBaseUrl: String? = null
    private var playDataToRestore: Pair<Track, UserItemDataDto>? = null

    @After
    fun backOnlineAndClean() = runBlocking<Unit> {
        realBaseUrl?.let { url -> onMain { apiClient.update(baseUrl = url) } }
        onMain { koinGet<PlaybackController>().stop() }
        playDataToRestore?.let { (track, original) ->
            apiClient.playStateApi.markUnplayedItem(track.id.toUUID())
            apiClient.itemsApi.updateItemUserData(
                itemId = track.id.toUUID(),
                data = UpdateUserItemDataDto(
                    playCount = original.playCount,
                    played = original.played,
                    lastPlayedDate = original.lastPlayedDate,
                ),
            )
        }
        koinGet<OfflinePlayReporter>().clear()
        repository.removeAll()
    }

    /** The album with the fewest bytes to fetch, and its songs. Needs a signed-in client. */
    private fun smallestAlbum(): Pair<Album, List<Track>> = runBlocking {
        val sizes = apiClient.itemsApi.getItems(
            includeItemTypes = listOf(BaseItemKind.AUDIO),
            recursive = true,
            fields = listOf(ItemFields.MEDIA_SOURCES),
        ).content.items.groupBy { it.albumId?.toString()?.replace("-", "") }
            .mapValues { (_, tracks) -> tracks.sumOf { it.mediaSources?.firstOrNull()?.size ?: Long.MAX_VALUE / 1_000 } }
        val album = catalog.albums().minBy { sizes[it.id.replace("-", "")] ?: Long.MAX_VALUE }
        album to catalog.albumTracks(album.id)
    }

    private fun download(album: Album, tracks: List<Track>) {
        runBlocking { repository.downloadAlbum(album, tracks) }
        composeRule.waitUntil(DOWNLOAD_TIMEOUT_MS) {
            runBlocking { repository.status(album.id).first() } == DownloadStatus.Downloaded
        }
    }

    /** Every request now fails at once (connection refused), like a phone with no signal. */
    private fun goOffline() {
        realBaseUrl = apiClient.baseUrl
        onMain { apiClient.update(baseUrl = UNREACHABLE) }
    }

    private fun signedInOnHome() {
        connectAndLogin()
        waitForComposeText("Recently added")
    }

    @Test
    fun anAlbumDownloadsFromItsPageAndAppearsUnderDownloaded() {
        connectAndLoginToLibrary()
        val (album, tracks) = smallestAlbum()
        openAlbum(album.title, firstTrack = tracks.first().title)

        composeRule.onNodeWithContentDescription("Download").performClick()

        // The button's label: rows are only marked (by description), so the text is the button's alone.
        waitForComposeText("Downloaded", timeoutMillis = DOWNLOAD_TIMEOUT_MS)
        assertEquals(DownloadStatus.Downloaded, runBlocking { repository.status(album.id).first() })
        pressBack()
        composeRule.onNodeWithText("Downloaded").performScrollTo().performClick()
        waitForComposeText(album.title)
    }

    @Smoke
    @Test
    fun offlineTheLibraryIsTheDownloadsAndTheyPlay() {
        signedInOnHome()
        val (album, tracks) = smallestAlbum()
        download(album, tracks)
        goOffline()

        composeRule.onNodeWithText("Library").performClick()

        waitForComposeText("Offline · showing your downloads")
        waitForComposeText(album.title)
        assertEquals(1, composeRule.onAllNodes(hasText(album.title)).fetchSemanticsNodes().size)
        composeRule.onNodeWithText(album.title).performClick()
        waitForComposeText(tracks.first().title)
        composeRule.onNodeWithText(tracks.first().title).performClick()
        val playback: PlaybackController = koinGet()
        composeRule.waitUntil(PLAY_TIMEOUT_MS) { onMain { playback.state.value.isPlaying && playback.positionMs > 2_000L } }
        onMain { assertEquals(tracks.first().title, playback.state.value.currentTrack?.title) }
    }

    @Test
    fun backOnlineTheBannerGoes() {
        signedInOnHome()
        val (album, tracks) = smallestAlbum()
        download(album, tracks)
        goOffline()
        composeRule.onNodeWithText("Library").performClick()
        waitForComposeText("Offline · showing your downloads")

        onMain { apiClient.update(baseUrl = realBaseUrl) }

        composeRule.waitUntil(ServerConnectivity.RETRY_INTERVAL_MS * 2) {
            composeRule.onAllNodes(hasText("Offline · showing your downloads")).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun aPlayMadeOfflineReachesTheServerOnceItIsBack() {
        signedInOnHome()
        val (album, tracks) = smallestAlbum()
        val track = tracks.first()
        val before = runBlocking { apiClient.itemsApi.getItemUserData(track.id.toUUID()).content }
        playDataToRestore = track to before
        download(album, tracks)
        goOffline()
        composeRule.onNodeWithText("Library").performClick()
        waitForComposeText("Offline · showing your downloads")

        // The listening threshold takes minutes of real play; report the counted play directly.
        runBlocking { koinGet<OfflinePlayReporter>().started(track.id, positionMs = 0L, isPaused = false) }
        onMain { apiClient.update(baseUrl = realBaseUrl) }

        composeRule.waitUntil(ServerConnectivity.RETRY_INTERVAL_MS * 3) {
            runBlocking { apiClient.itemsApi.getItemUserData(track.id.toUUID()).content.playCount } == before.playCount + 1
        }
    }

    @Test
    fun backOnlineTheLibraryShowsTheServersAlbumsAgainWithoutReopening() {
        signedInOnHome()
        val (album, tracks) = smallestAlbum()
        val notDownloaded = runBlocking { catalog.albums() }.first { it.id != album.id }
        download(album, tracks)
        goOffline()
        composeRule.onNodeWithText("Library").performClick()
        waitForComposeText("Offline · showing your downloads")
        assertEquals(0, composeRule.onAllNodes(hasText(notDownloaded.title)).fetchSemanticsNodes().size)

        onMain { apiClient.update(baseUrl = realBaseUrl) }

        composeRule.waitUntil(ServerConnectivity.RETRY_INTERVAL_MS * 2) {
            composeRule.onAllNodes(hasText("Offline · showing your downloads")).fetchSemanticsNodes().isEmpty()
        }
        scrollToAndClick(notDownloaded.title)
    }

    @Test
    fun offlineHomeShowsYourDownloads() {
        signedInOnHome()
        val (album, tracks) = smallestAlbum()
        download(album, tracks)
        goOffline()
        // Something has to try the server for the app to find out it's gone.
        composeRule.onNodeWithText("Library").performClick()
        waitForComposeText("Offline · showing your downloads")

        composeRule.onNode(hasText("Home") and isSelectable()).performClick()

        waitForComposeText("Your downloads")
        waitForComposeText(album.title)
        assertEquals(0, composeRule.onAllNodes(hasText("Recently played")).fetchSemanticsNodes().size)
    }

    private companion object {
        const val DOWNLOAD_TIMEOUT_MS = 120_000L
        const val PLAY_TIMEOUT_MS = 30_000L
        const val UNREACHABLE = "http://127.0.0.1:9"
    }
}
