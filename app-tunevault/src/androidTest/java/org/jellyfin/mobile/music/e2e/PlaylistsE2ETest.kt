package org.jellyfin.mobile.music.e2e

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.music.catalog.MusicCatalog
import org.jellyfin.mobile.music.catalog.PlaylistStore
import org.jellyfin.mobile.music.domain.Track
import org.jellyfin.mobile.music.playback.PlaybackController
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

/** Playlists against the real Vault. Every playlist made here is named "TuneVault e2e …" and deleted. */
@CollectionsArea
class PlaylistsE2ETest : LoggedInE2ETest() {

    private val store: PlaylistStore get() = koinGet()
    private val name = "TuneVault e2e ${UUID.randomUUID().toString().take(8)}"

    @After
    fun deleteE2ePlaylists() = runBlocking<Unit> {
        runCatching { store.playlists().filter { it.name.startsWith("TuneVault e2e") }.forEach { store.deletePlaylist(it.id) } }
    }

    private fun purpleBirdTracks(): List<Track> = runBlocking {
        val catalog: MusicCatalog = koinGet()
        catalog.albumTracks(catalog.albums().first { it.title == "The Purple Bird" }.id)
    }

    private fun entries(playlistId: String) = runBlocking { store.playlistEntries(playlistId).map { it.track.title } }

    private fun openPlaylistsTab() {
        composeRule.onNodeWithText("Playlists").performClick()
        waitForComposeText("New playlist")
    }

    @Test
    fun aPlaylistCanBeCreatedFromThePlaylistsTab() {
        connectAndLoginToLibrary()
        openPlaylistsTab()

        composeRule.onNodeWithText("New playlist").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput(name)
        composeRule.onNodeWithText("Create").performClick()

        waitForComposeText(name)
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { runBlocking { store.playlists().any { it.name == name } } }
    }

    @Test
    fun aSongAddedFromItsMenuIsInThePlaylistAndPlaysFromIt() {
        connectAndLoginToLibrary()
        val playlistId = runBlocking { store.createPlaylist(name, emptyList()) }
        openAlbum("The Purple Bird", firstTrack = "Turned To Dust (Rolling On)")

        composeRule.onNodeWithText("London May").performTouchInput { longClick() }
        composeRule.onNodeWithText("Add to playlist…").performClick()
        waitForComposeText(name)
        composeRule.onNodeWithText(name).performClick()

        composeRule.waitUntil(WAIT_TIMEOUT_MS) { entries(playlistId) == listOf("London May") }

        // Open it from the Playlists tab and play it.
        androidx.test.espresso.Espresso.pressBack()
        waitForComposeText("Albums")
        openPlaylistsTab()
        scrollToAndClick(name)
        waitForComposeText("1 song")
        composeRule.onNodeWithText("Play").performClick()
        val playback: PlaybackController = koinGet()
        composeRule.waitUntil(30_000L) { onMain { playback.state.value.isPlaying } }
        onMain { assertEquals("London May", playback.state.value.currentTrack?.title) }
    }

    @Test
    fun aSongCanBeRemovedFromAPlaylist() {
        connectAndLoginToLibrary()
        val tracks = purpleBirdTracks()
        val playlistId = runBlocking { store.createPlaylist(name, listOf(tracks[0].id, tracks[1].id)) }
        openPlaylistsTab()
        scrollToAndClick(name)
        waitForComposeText("London May")

        composeRule.onNodeWithText("London May").performTouchInput { longClick() }
        composeRule.onNodeWithText("Remove from playlist").performClick()

        composeRule.waitUntil(WAIT_TIMEOUT_MS) { entries(playlistId) == listOf(tracks[0].title) }
    }

    /**
     * Guards the outcome the user sees. It can't prove TuneVault skips the add itself: this server
     * sometimes ignores repeats on its own (see PlaylistStore), so a missing app-side check can still
     * pass here. AddToPlaylistViewModelTest proves the check, against a fake that records every write.
     */
    @Test
    fun addingASongThePlaylistAlreadyHasDoesNotDuplicateIt() {
        connectAndLoginToLibrary()
        val londonMay = purpleBirdTracks().first { it.title == "London May" }
        val playlistId = runBlocking { store.createPlaylist(name, listOf(londonMay.id)) }
        openAlbum("The Purple Bird", firstTrack = "Turned To Dust (Rolling On)")

        composeRule.onNodeWithText("London May").performTouchInput { longClick() }
        composeRule.onNodeWithText("Add to playlist…").performClick()
        waitForComposeText(name)
        composeRule.onNodeWithText(name).performClick()
        Thread.sleep(2_000) // give a wrong add the chance to land before checking it didn't

        assertEquals(listOf("London May"), entries(playlistId))
    }
}
