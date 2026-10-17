package org.jellyfin.mobile.music.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import org.jellyfin.mobile.music.app.LibraryPreferences
import org.jellyfin.mobile.music.domain.AlbumSort
import org.jellyfin.mobile.music.playback.PlaybackController
import org.jellyfin.mobile.testing.VaultFixtures
import org.jellyfin.mobile.testing.VaultFixtures.PatternSuite
import org.jellyfin.mobile.testing.VaultFixtures.SignalPath
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Library tabs, artist pages and songs against the test server. */
@BrowseArea
class LibraryE2ETest : LoggedInE2ETest() {

    private fun waitForTextContaining(text: String) {
        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeRule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun artistsTabLeadsToAnArtistAndTheirAlbumAndBackRetracesIt() {
        connectAndLoginToLibrary()
        waitForComposeText("Artists")
        composeRule.onNodeWithText("Artists").performClick()

        scrollToAndClick(SignalPath.ARTIST)
        waitForComposeText(SignalPath.TITLE)
        composeRule.onNodeWithText(SignalPath.TITLE).performClick()
        waitForTextContaining(" tracks")

        pressBack()
        waitForComposeText(SignalPath.ARTIST)
        composeRule.onNodeWithText(SignalPath.TITLE).assertIsDisplayed()

        pressBack()
        waitForComposeText(PatternSuite.ARTIST) // back on the Artists list
        composeRule.onNodeWithText(SignalPath.ARTIST).assertIsDisplayed()
    }

    @Test
    fun songsTabShufflesTheWholeLibrary() {
        connectAndLoginToLibrary()
        waitForComposeText("Songs")
        composeRule.onNodeWithText("Songs").performClick()
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
    fun theAlbumSortChoiceIsKept() {
        connectAndLoginToLibrary()
        waitForComposeText("Sort: Title")

        composeRule.onNodeWithText("Sort: Title").performClick()
        composeRule.onNodeWithText("Year").performClick()
        waitForComposeText("Sort: Year")

        composeRule.activityRule.scenario.recreate()

        waitForComposeText("Sort: Year")
        assertEquals(AlbumSort.YEAR, koinGet<LibraryPreferences>().albumSort)
    }
}
