package org.jellyfin.mobile.music.e2e

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.common.server.toServerDateTime
import org.jellyfin.mobile.music.catalog.MusicCatalog
import org.jellyfin.mobile.music.home.HomeScreenTags
import org.jellyfin.mobile.music.playback.PlaybackController
import org.jellyfin.mobile.testing.VaultFixtures
import org.jellyfin.mobile.testing.VaultFixtures.PatternSuite
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.jellyfin.sdk.model.serializer.toUUID
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

/** Home against the test server. One test writes a track's play state and restores it. */
@BrowseArea
class HomeE2ETest : LoggedInE2ETest() {

    private val apiClient: ApiClient get() = koinGet()

    /** The bar item, not the "Home" header title. */
    private val homeTab = hasText("Home") and isSelectable()
    private var markedTrack: UUID? = null
    private var original: UserItemDataDto? = null

    @After
    fun restorePlayState() = runBlocking<Unit> {
        val id = markedTrack ?: return@runBlocking
        apiClient.playStateApi.markUnplayedItem(id)
        original?.takeIf { it.played }?.let { apiClient.playStateApi.markPlayedItem(id, datePlayed = it.lastPlayedDate) }
    }

    @Test
    fun opensOnHome() {
        connectAndLogin()

        waitForComposeText("Recently added")
        composeRule.onNode(homeTab).assertIsSelected()
    }

    @Test
    fun anAlbumJustPlayedShowsUnderRecentlyPlayed() {
        connectAndLogin()
        waitForComposeText("Recently played")
        val catalog: MusicCatalog = koinGet()
        val knownSong = runBlocking {
            val album = catalog.albums().first { it.title == PatternSuite.TITLE }
            catalog.albumTracks(album.id).first { it.title == PatternSuite.KNOWN_SONG }
        }
        val id = knownSong.id.toUUID()
        markedTrack = id
        original = runBlocking { apiClient.itemsApi.getItems(ids = listOf(id)).content.items.single().userData }
        runBlocking { apiClient.playStateApi.markPlayedItem(id, datePlayed = Instant.now().toServerDateTime()) }

        // Home refreshes whenever it's shown: leave and come back.
        composeRule.onNodeWithText("Library").performClick()
        pressBack()

        val onShelf = hasText(PatternSuite.TITLE) and hasAnyAncestor(hasTestTag(HomeScreenTags.RECENTLY_PLAYED))
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { composeRule.onAllNodes(onShelf).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun shuffleAllShufflesTheWholeLibrary() {
        connectAndLogin()
        waitForComposeText("Shuffle all")

        composeRule.onNodeWithText("Shuffle all").performClick()

        val playback: PlaybackController = koinGet()
        composeRule.waitUntil(30_000L) { onMain { playback.state.value.isPlaying && playback.positionMs > 500L } }
        onMain {
            assertTrue(playback.state.value.queue.isShuffled)
            assertTrue("whole library queued", playback.state.value.queue.tracks.size > VaultFixtures.MIN_MUSIC_TRACKS)
        }
    }

    @Test
    fun backFromTheLibraryReturnsHome() {
        connectAndLoginToLibrary()

        pressBack()

        waitForComposeText("Recently played")
        composeRule.onNode(homeTab).assertIsSelected()
    }
}
